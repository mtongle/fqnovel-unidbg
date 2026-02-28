package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.FQApiProperties;
import com.anjia.unidbgserver.dto.DeviceInfo;
import com.anjia.unidbgserver.dto.DeviceRegisterRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        return devicePool.get(idx % devicePool.size());
    }

    public DeviceInfo findDeviceById(String deviceId) {
        if (!notBlank(deviceId)) {
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
        if (fqApiProperties.getDevicePool() == null || fqApiProperties.getDevicePool().getSize() == null) {
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
            && Boolean.TRUE.equals(fqApiProperties.getDevicePool().getEnabled());
    }

    private boolean isSameDevice(DeviceInfo left, DeviceInfo right) {
        if (left == null || right == null) {
            return false;
        }

        boolean byDeviceId = notBlank(left.getDeviceId()) && left.getDeviceId().equals(right.getDeviceId());
        boolean byInstallId = notBlank(left.getInstallId()) && left.getInstallId().equals(right.getInstallId());
        boolean byCdid = notBlank(left.getCdid()) && left.getCdid().equals(right.getCdid());

        return byDeviceId || byInstallId || byCdid;
    }

    private DeviceInfo buildFallbackDevice() {
        FQApiProperties.Device device = fqApiProperties.getDevice();
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
        if (!notBlank(userAgent)) {
            return "13";
        }

        Pattern pattern = Pattern.compile("Android\\s+([^;\\s]+)");
        Matcher matcher = pattern.matcher(userAgent);
        if (matcher.find()) {
            return matcher.group(1);
        }

        return "13";
    }

    private boolean notBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
