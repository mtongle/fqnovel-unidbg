package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.DeviceInfo;
import com.anjia.unidbgserver.dto.FqRegisterKeyPayload;
import com.anjia.unidbgserver.dto.FqRegisterKeyResponse;
import com.anjia.unidbgserver.dto.FqVariable;
import com.anjia.unidbgserver.utils.FQApiUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * FQNovel RegisterKey缓存服务
 * 在启动时获取registerkey并缓存，支持keyver比较和自动刷新
 */
@Slf4j
@Service
public class FQRegisterKeyService {

    @Resource(name = "fqEncryptWorker")
    private FQEncryptServiceWorker fqEncryptServiceWorker;

    @Resource
    private FQApiUtils fqApiUtils;

    @Resource
    private DevicePoolService devicePoolService;

    private final RestTemplate restTemplate = new RestTemplate();

    private final Map<String, DeviceRegisterKeyCache> registerKeyCacheByDevice = new ConcurrentHashMap<>();

    private static class DeviceRegisterKeyCache {
        private final Map<Long, FqRegisterKeyResponse> cachedRegisterKeys = new ConcurrentHashMap<>();
        private volatile FqRegisterKeyResponse currentRegisterKey;
        private volatile Long keyRegisterTs;
    }

    /**
     * 启动时初始化registerkey
     */
    @PostConstruct
    public void initialize() {
        log.info("初始化FQRegisterKeyService，获取初始registerkey...");
        try {
            DeviceInfo initDevice = devicePoolService.nextDevice();
            FqRegisterKeyResponse response = fetchRegisterKey(initDevice);
            if (response != null && response.getData() != null) {
                DeviceRegisterKeyCache deviceCache = getOrCreateDeviceCache(initDevice);
                long keyver = response.getData().getKeyver();
                deviceCache.cachedRegisterKeys.put(keyver, response);
                deviceCache.currentRegisterKey = response;
                long keyRegisterTs = normalizeKeyRegisterTs(deviceCache.keyRegisterTs);
                log.debug("初始registerkey获取成功，deviceCacheKey={}, keyver: {}, key_register_ts={}, content: {}",
                    buildDeviceCacheKey(initDevice),
                    keyver,
                    keyRegisterTs,
                    response.getData().getKey());
            } else {
                log.error("初始registerkey获取失败，响应为空");
            }
        } catch (Exception e) {
            log.error("初始化registerkey失败", e);
        }
    }

    /**
     * 获取registerkey（兼容旧接口，默认取当前轮询设备）
     *
     * @param requiredKeyver 需要的keyver，如果为null则使用当前缓存的key
     * @return RegisterKey响应
     */
    public synchronized FqRegisterKeyResponse getRegisterKey(Long requiredKeyver) throws Exception {
        DeviceInfo currentDevice = devicePoolService.nextDevice();
        return getRegisterKey(currentDevice, requiredKeyver);
    }

    /**
     * 获取registerkey（按设备维度）
     *
     * @param deviceInfo 设备信息
     * @param requiredKeyver 需要的keyver，如果为null则使用当前缓存的key
     * @return RegisterKey响应
     */
    public synchronized FqRegisterKeyResponse getRegisterKey(DeviceInfo deviceInfo, Long requiredKeyver) throws Exception {
        String deviceCacheKey = buildDeviceCacheKey(deviceInfo);
        DeviceRegisterKeyCache deviceCache = getOrCreateDeviceCache(deviceInfo);

        if (requiredKeyver == null) {
            if (deviceCache.currentRegisterKey != null) {
                return deviceCache.currentRegisterKey;
            }
            return refreshRegisterKey(deviceInfo);
        }

        FqRegisterKeyResponse cached = deviceCache.cachedRegisterKeys.get(requiredKeyver);
        if (cached != null) {
            log.debug("使用缓存的registerkey，deviceCacheKey={}, keyver={}", deviceCacheKey, requiredKeyver);
            return cached;
        }

        if (deviceCache.currentRegisterKey == null || deviceCache.currentRegisterKey.getData().getKeyver() != requiredKeyver) {
            log.info("当前registerkey keyver ({}) 与需要的keyver ({}) 不匹配，刷新registerkey。deviceCacheKey={}",
                deviceCache.currentRegisterKey != null ? deviceCache.currentRegisterKey.getData().getKeyver() : "null",
                requiredKeyver,
                deviceCacheKey);
            return refreshRegisterKey(deviceInfo);
        }

        return deviceCache.currentRegisterKey;
    }

    /**
     * 刷新registerkey（兼容旧接口，默认取当前轮询设备）
     *
     * @return 新的RegisterKey响应
     */
    public synchronized FqRegisterKeyResponse refreshRegisterKey() throws Exception {
        DeviceInfo currentDevice = devicePoolService.nextDevice();
        return refreshRegisterKey(currentDevice);
    }

    /**
     * 刷新registerkey（按设备维度）
     *
     * @param deviceInfo 设备信息
     * @return 新的RegisterKey响应
     */
    public synchronized FqRegisterKeyResponse refreshRegisterKey(DeviceInfo deviceInfo) throws Exception {
        String deviceCacheKey = buildDeviceCacheKey(deviceInfo);
        log.info("刷新registerkey，deviceCacheKey={}, deviceId={}", deviceCacheKey, safeDeviceId(deviceInfo));

        FqRegisterKeyResponse response = fetchRegisterKey(deviceInfo);
        if (response != null && response.getData() != null) {
            DeviceRegisterKeyCache deviceCache = getOrCreateDeviceCache(deviceInfo);
            long keyver = response.getData().getKeyver();
            deviceCache.cachedRegisterKeys.put(keyver, response);
            deviceCache.currentRegisterKey = response;
            long keyRegisterTs = normalizeKeyRegisterTs(deviceCache.keyRegisterTs);
            log.info("registerkey刷新成功，deviceCacheKey={}, deviceId={}, 新keyver={}, key_register_ts={}",
                deviceCacheKey,
                safeDeviceId(deviceInfo),
                keyver,
                keyRegisterTs);
            return response;
        }

        throw new Exception("刷新registerkey失败，响应为空");
    }

    /**
     * 实际获取registerkey的方法
     *
     * @param fixedDevice 指定设备（为null时走设备池轮询）
     * @return RegisterKey响应
     */
    private FqRegisterKeyResponse fetchRegisterKey(DeviceInfo fixedDevice) throws Exception {
        int maxAttempts = fixedDevice != null ? 2 : Math.max(2, devicePoolService.getTargetPoolSize() + 1);
        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            DeviceInfo currentDevice = fixedDevice != null ? fixedDevice : devicePoolService.nextDevice();
            try {
                FqVariable var = new FqVariable(currentDevice);

                String url = fqApiUtils.getBaseUrl() + "/reading/crypt/registerkey";
                Map<String, String> params = fqApiUtils.buildCommonApiParams(var);
                String fullUrl = fqApiUtils.buildUrlWithParams(url, params);

                long currentTime = System.currentTimeMillis();
                Map<String, String> headers = fqApiUtils.buildRegisterKeyHeaders(currentDevice, currentTime);
                Map<String, String> signedHeaders = fqEncryptServiceWorker.generateSignatureHeaders(fullUrl, headers).get();

                HttpHeaders httpHeaders = new HttpHeaders();
                signedHeaders.forEach(httpHeaders::set);
                headers.forEach(httpHeaders::set);

                FqRegisterKeyPayload payload = new FqRegisterKeyPayload(var);
                HttpEntity<FqRegisterKeyPayload> entity = new HttpEntity<>(payload, httpHeaders);

                log.debug("发送registerkey请求到: {}", fullUrl);
                log.debug("请求时间戳: {}, deviceCacheKey={}, deviceId={}",
                    currentTime,
                    buildDeviceCacheKey(currentDevice),
                    safeDeviceId(currentDevice));

                ResponseEntity<FqRegisterKeyResponse> response = restTemplate.exchange(
                    fullUrl, HttpMethod.POST, entity, FqRegisterKeyResponse.class);

                FqRegisterKeyResponse body = response.getBody();
                if (body == null || body.getData() == null) {
                    throw new RuntimeException("EMPTY_RESPONSE");
                }

                DeviceRegisterKeyCache deviceCache = getOrCreateDeviceCache(currentDevice);
                deviceCache.keyRegisterTs = currentTime;
                long keyRegisterTs = normalizeKeyRegisterTs(deviceCache.keyRegisterTs);

                log.debug("registerkey请求响应: code={}, message={}, keyver={}, deviceCacheKey={}, deviceId={}, key_register_ts={}",
                    body.getCode(), body.getMessage(),
                    body.getData() != null ? body.getData().getKeyver() : "null",
                    buildDeviceCacheKey(currentDevice),
                    safeDeviceId(currentDevice),
                    keyRegisterTs);

                return body;
            } catch (Exception e) {
                lastException = e;
                if (isEmptyResponseError(e)) {
                    log.warn("registerkey接口空响应，attempt={}/{}, deviceId={}, deviceCacheKey={}",
                        attempt,
                        maxAttempts,
                        safeDeviceId(currentDevice),
                        buildDeviceCacheKey(currentDevice));

                    if (fixedDevice == null) {
                        devicePoolService.removeAndReplenish(currentDevice, "registerkey empty response");
                    }
                    continue;
                }
                throw e;
            }
        }

        if (lastException != null) {
            throw new RuntimeException("registerkey接口重试后仍失败: " + lastException.getMessage(), lastException);
        }
        throw new RuntimeException("registerkey接口重试后仍为空响应");
    }

    private boolean isEmptyResponseError(Exception e) {
        String message = e.getMessage();
        return "EMPTY_RESPONSE".equals(message)
            || (message != null && message.contains("No content to map due to end-of-input"));
    }

    /**
     * 获取指定keyver的解密密钥（兼容旧接口，默认取当前轮询设备）
     *
     * @param requiredKeyver 需要的keyver
     * @return 解密密钥（十六进制字符串）
     */
    public String getDecryptionKey(Long requiredKeyver) throws Exception {
        DeviceInfo currentDevice = devicePoolService.nextDevice();
        return getDecryptionKey(currentDevice, requiredKeyver);
    }

    /**
     * 获取指定keyver的解密密钥（按设备维度）
     *
     * @param deviceInfo 设备信息
     * @param requiredKeyver 需要的keyver
     * @return 解密密钥（十六进制字符串）
     */
    public String getDecryptionKey(DeviceInfo deviceInfo, Long requiredKeyver) throws Exception {
        FqRegisterKeyResponse registerKeyResponse = getRegisterKey(deviceInfo, requiredKeyver);
        return registerKeyResponse.getData().getKey();
    }

    /**
     * 获取当前的解密密钥（兼容旧接口，默认取当前轮询设备）
     *
     * @return 解密密钥（十六进制字符串）
     */
    public String getCurrentDecryptionKey() throws Exception {
        DeviceInfo currentDevice = devicePoolService.nextDevice();
        return getCurrentDecryptionKey(currentDevice);
    }

    /**
     * 获取当前的解密密钥（按设备维度）
     */
    public String getCurrentDecryptionKey(DeviceInfo deviceInfo) throws Exception {
        return getDecryptionKey(deviceInfo, null);
    }

    /**
     * 确保指定设备具备可用registerkey上下文，并返回 key_register_ts
     */
    public synchronized long ensureRegisterKeyReady(DeviceInfo deviceInfo) throws Exception {
        String deviceCacheKey = buildDeviceCacheKey(deviceInfo);
        DeviceRegisterKeyCache deviceCache = getOrCreateDeviceCache(deviceInfo);

        boolean hasCurrentKey = deviceCache.currentRegisterKey != null && deviceCache.currentRegisterKey.getData() != null;
        long currentKeyRegisterTs = normalizeKeyRegisterTs(deviceCache.keyRegisterTs);
        if (!hasCurrentKey || currentKeyRegisterTs <= 0) {
            log.info("registerkey上下文缺失，触发预热刷新，deviceCacheKey={}, deviceId={}, hasCurrentKey={}, key_register_ts={}",
                deviceCacheKey,
                safeDeviceId(deviceInfo),
                hasCurrentKey,
                currentKeyRegisterTs);
            refreshRegisterKey(deviceInfo);
            deviceCache = getOrCreateDeviceCache(deviceInfo);
        }

        long keyRegisterTs = normalizeKeyRegisterTs(deviceCache.keyRegisterTs);
        Long currentKeyver = deviceCache.currentRegisterKey != null && deviceCache.currentRegisterKey.getData() != null
            ? deviceCache.currentRegisterKey.getData().getKeyver()
            : null;
        log.debug("registerkey上下文已就绪，deviceCacheKey={}, deviceId={}, currentKeyver={}, key_register_ts={}",
            deviceCacheKey,
            safeDeviceId(deviceInfo),
            currentKeyver,
            keyRegisterTs);
        return keyRegisterTs;
    }

    /**
     * 获取指定设备当前 key_register_ts
     */
    public synchronized long getKeyRegisterTs(DeviceInfo deviceInfo) {
        DeviceRegisterKeyCache deviceCache = getOrCreateDeviceCache(deviceInfo);
        return normalizeKeyRegisterTs(deviceCache.keyRegisterTs);
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        registerKeyCacheByDevice.clear();
        log.info("registerkey缓存已清除（按设备维度）");
    }

    /**
     * 获取缓存状态信息
     */
    public Map<String, Object> getCacheStatus() {
        Map<String, Object> status = new HashMap<>();
        Map<String, Object> devices = new LinkedHashMap<>();
        int totalCachedKeyvers = 0;

        for (Map.Entry<String, DeviceRegisterKeyCache> entry : registerKeyCacheByDevice.entrySet()) {
            DeviceRegisterKeyCache deviceCache = entry.getValue();
            Map<String, Object> perDevice = new HashMap<>();
            perDevice.put("cachedKeyversCount", deviceCache.cachedRegisterKeys.size());
            perDevice.put("cachedKeyvers", deviceCache.cachedRegisterKeys.keySet());
            perDevice.put("currentKeyver", deviceCache.currentRegisterKey != null ? deviceCache.currentRegisterKey.getData().getKeyver() : null);
            perDevice.put("keyRegisterTs", normalizeKeyRegisterTs(deviceCache.keyRegisterTs));
            devices.put(entry.getKey(), perDevice);
            totalCachedKeyvers += deviceCache.cachedRegisterKeys.size();
        }

        status.put("cachedDevicesCount", registerKeyCacheByDevice.size());
        status.put("cachedKeyversCount", totalCachedKeyvers);
        status.put("devices", devices);
        return status;
    }

    private DeviceRegisterKeyCache getOrCreateDeviceCache(DeviceInfo deviceInfo) {
        String cacheKey = buildDeviceCacheKey(deviceInfo);
        return registerKeyCacheByDevice.computeIfAbsent(cacheKey, key -> new DeviceRegisterKeyCache());
    }

    static String buildDeviceCacheKey(DeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return "fallback:default";
        }

        if (notBlank(deviceInfo.getDeviceId())) {
            return "deviceId:" + deviceInfo.getDeviceId().trim();
        }

        if (notBlank(deviceInfo.getInstallId())) {
            return "installId:" + deviceInfo.getInstallId().trim();
        }

        if (notBlank(deviceInfo.getCdid())) {
            return "cdid:" + deviceInfo.getCdid().trim();
        }

        String deviceBrand = notBlank(deviceInfo.getDeviceBrand()) ? deviceInfo.getDeviceBrand().trim() : "unknownBrand";
        String deviceType = notBlank(deviceInfo.getDeviceType()) ? deviceInfo.getDeviceType().trim() : "unknownType";
        return "fallback:" + deviceBrand + "|" + deviceType;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private long normalizeKeyRegisterTs(Long keyRegisterTs) {
        return keyRegisterTs != null && keyRegisterTs > 0 ? keyRegisterTs : 0L;
    }

    private String safeDeviceId(DeviceInfo deviceInfo) {
        return deviceInfo != null ? deviceInfo.getDeviceId() : null;
    }
}
