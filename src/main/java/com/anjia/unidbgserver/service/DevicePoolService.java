package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.FQApiProperties;
import com.anjia.unidbgserver.dto.DeviceInfo;
import com.anjia.unidbgserver.dto.DeviceRegisterRequest;
import com.anjia.unidbgserver.utils.CommonUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;

/**
 * 设备池服务
 * 负责设备轮询、故障剔除与自动补充
 */
@Slf4j
@Service
public class DevicePoolService {

    @Resource
    private FQApiProperties fqApiProperties;

    @Resource
    private DeviceGeneratorService deviceGeneratorService;

    @Resource
    private DeviceRegisterClientService deviceRegisterClientService;

    private final List<DeviceInfo> devicePool = new CopyOnWriteArrayList<>();
    private final AtomicInteger roundRobinIndex = new AtomicInteger(0);
    private final Object poolLock = new Object();

    /**
     * 最近一次调用 nextDevice() 返回的设备
     * 用于图片代理：代理 URL 的签名基于此次调用使用的设备，
     * 需用同一设备的 Cookie/UA 才能通过 CDN 校验
     */
    private volatile DeviceInfo lastUsedDevice;

    @PostConstruct
    public void initDevicePool() {
        if (!isEnabled()) {
            log.info("设备池未启用，使用静态配置设备");
            return;
        }
        rebuildPool();
    }

    public DeviceInfo nextDevice() {
        if (!isEnabled()) {
            return buildFallbackDevice();
        }

        ensurePoolReady();

        if (devicePool.isEmpty()) {
            log.warn("设备池为空，退回静态配置设备");
            return buildFallbackDevice();
        }

        int idx = Math.abs(roundRobinIndex.getAndIncrement());
        DeviceInfo device = devicePool.get(idx % devicePool.size());
        this.lastUsedDevice = device;
        return device;
    }

    /**
     * 获取上次 nextDevice() 返回的设备
     * 图片代理在取 CDN 图片时需使用同一设备的 Cookie/UA
     */
    public DeviceInfo getLastUsedDevice() {
        DeviceInfo device = lastUsedDevice;
        return device != null ? device : buildFallbackDevice();
    }


    public DeviceInfo findDeviceById(String deviceId) {
        if (!CommonUtils.isNotBlank(deviceId)) {
            return null;
        }

        String normalizedDeviceId = deviceId.trim();

        if (!isEnabled()) {
            DeviceInfo fallback = buildFallbackDevice();
            return normalizedDeviceId.equals(fallback.getDeviceId()) ? fallback : null;
        }

        ensurePoolReady();
        for (DeviceInfo deviceInfo : devicePool) {
            if (deviceInfo != null && normalizedDeviceId.equals(deviceInfo.getDeviceId())) {
                return deviceInfo;
            }
        }

        return null;
    }

    public void removeAndReplenish(DeviceInfo badDevice, String reason) {
        if (!isEnabled() || badDevice == null) {
            return;
        }

        synchronized (poolLock) {
            int beforeSize = devicePool.size();
            devicePool.removeIf(device -> isSameDevice(device, badDevice));
            int removed = beforeSize - devicePool.size();
            if (removed > 0) {
                log.warn("设备已从池中移除，reason={}, removed={}, deviceId={}, installId={}",
                    reason, removed, badDevice.getDeviceId(), badDevice.getInstallId());
            }
            replenishPoolLocked();
        }
    }

    public void rebuildPool() {
        synchronized (poolLock) {
            devicePool.clear();
            roundRobinIndex.set(0);
            replenishPoolLocked();
            log.info("设备池初始化完成，current={}, target={}", devicePool.size(), getTargetPoolSize());
        }
    }

    public boolean removeDeviceById(String deviceId) {
        if (!isEnabled() || !CommonUtils.isNotBlank(deviceId)) {
            return false;
        }
        synchronized (poolLock) {
            int beforeSize = devicePool.size();
            devicePool.removeIf(device -> deviceId.trim().equals(device.getDeviceId()));
            int removed = beforeSize - devicePool.size();
            if (removed > 0) {
                log.info("手动移除设备 deviceId={}, 池中剩余 {} 个设备", deviceId, devicePool.size());
                replenishPoolLocked();
                return true;
            }
            return false;
        }
    }

    public boolean addDevice() {
        if (!isEnabled()) {
            return false;
        }
        synchronized (poolLock) {
            if (devicePool.size() >= getTargetPoolSize()) {
                log.info("设备池已满 ({}), 跳过添加", devicePool.size());
                return false;
            }
            DeviceInfo deviceInfo = createAndRegisterDevice();
            if (deviceInfo != null) {
                devicePool.add(deviceInfo);
                log.info("手动添加设备成功 deviceId={}, 当前池大小 {}", deviceInfo.getDeviceId(), devicePool.size());
                return true;
            }
            return false;
        }
    }

    public Map<String, Object> getPoolStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", isEnabled());
        status.put("targetSize", getTargetPoolSize());
        status.put("currentSize", devicePool.size());
        status.put("nextIndex", roundRobinIndex.get());
        status.put("devices", new ArrayList<>(devicePool));
        return status;
    }

    public int getTargetPoolSize() {
        if (fqApiProperties.getDevicePool() == null) {
            return 3;
        }
        return Math.max(1, fqApiProperties.getDevicePool().getSize());
    }

    private void ensurePoolReady() {
        synchronized (poolLock) {
            replenishPoolLocked();
        }
    }

    private void replenishPoolLocked() {
        if (!isEnabled()) {
            return;
        }

        int target = getTargetPoolSize();
        int attempts = 0;
        int maxAttempts = Math.max(target * 4, 8);

        while (devicePool.size() < target && attempts < maxAttempts) {
            attempts++;
            DeviceInfo deviceInfo = createAndRegisterDevice();
            if (deviceInfo != null) {
                devicePool.add(deviceInfo);
            }
        }

        if (devicePool.size() < target) {
            log.warn("设备池补充不足，current={}, target={}", devicePool.size(), target);
        }
    }

    private DeviceInfo createAndRegisterDevice() {
        DeviceRegisterRequest request = DeviceRegisterRequest.builder()
            .useRealAlgorithm(true)
            .useRealBrand(true)
            .autoUpdateConfig(false)
            .autoRestart(false)
            .build();

        DeviceInfo deviceInfo = deviceGeneratorService.generateDeviceInfo(request);
        if (deviceInfo == null) {
            return null;
        }

        boolean registered = deviceRegisterClientService.registerDevice(deviceInfo);
        if (!registered) {
            return null;
        }

        return deviceInfo;
    }

    private boolean isEnabled() {
        return fqApiProperties.getDevicePool() != null
            && fqApiProperties.getDevicePool().isEnabled();
    }

    private boolean isSameDevice(DeviceInfo left, DeviceInfo right) {
        if (left == null || right == null) {
            return false;
        }

        boolean byDeviceId = CommonUtils.isNotBlank(left.getDeviceId()) && left.getDeviceId().equals(right.getDeviceId());
        boolean byInstallId = CommonUtils.isNotBlank(left.getInstallId()) && left.getInstallId().equals(right.getInstallId());
        boolean byCdid = CommonUtils.isNotBlank(left.getCdid()) && left.getCdid().equals(right.getCdid());

        return byDeviceId || byInstallId || byCdid;
    }

    private DeviceInfo buildFallbackDevice() {
        FQApiProperties.Device device = fqApiProperties.getDevice();
        if (device == null) {
            device = new FQApiProperties.Device();
        }
        String userAgent = fqApiProperties.getUserAgent();
        String cookie = fqApiProperties.getCookie();

        return DeviceInfo.builder()
            .aid(device.getAid())
            .cdid(device.getCdid())
            .deviceBrand(device.getDeviceBrand())
            .deviceType(device.getDeviceType())
            .deviceId(device.getDeviceId())
            .installId(device.getInstallId())
            .resolution(device.getResolution())
            .dpi(device.getDpi())
            .hostAbi(device.getHostAbi())
            .romVersion(device.getRomVersion())
            .versionCode(device.getVersionCode())
            .versionName(device.getVersionName())
            .updateVersionCode(device.getUpdateVersionCode())
            .userAgent(userAgent)
            .cookie(cookie)
            .osVersion(extractAndroidVersion(userAgent))
            .osApi(null)
            .build();
    }

    private String extractAndroidVersion(String userAgent) {
        if (!CommonUtils.isNotBlank(userAgent)) {
            return "13";
        }

        Matcher matcher = CommonUtils.ANDROID_VERSION_PATTERN.matcher(userAgent);
        return matcher.find() ? matcher.group(1) : "13";
    }
}
