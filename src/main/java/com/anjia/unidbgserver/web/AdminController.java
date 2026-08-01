package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.config.AdminAuthFilter;
import com.anjia.unidbgserver.service.ConfigManagementService;
import com.anjia.unidbgserver.service.DeviceManagementService;
import com.anjia.unidbgserver.service.DevicePoolService;
import com.anjia.unidbgserver.service.FQEncryptServiceWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping(path = "/api/admin", produces = MediaType.APPLICATION_JSON_VALUE)
public class AdminController {

    @Autowired
    private ConfigManagementService configManagementService;

    @Autowired
    private DeviceManagementService deviceManagementService;

    @Autowired
    private DevicePoolService devicePoolService;

    @Autowired(required = false)
    private FQEncryptServiceWorker fqEncryptWorker;

    @Autowired(required = false)
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private AdminAuthFilter adminAuthFilter;

    @Value("${application.http-client.connect-timeout-ms:5000}")
    private int httpConnectTimeoutMs;

    @Value("${application.http-client.read-timeout-ms:15000}")
    private int httpReadTimeoutMs;

    @Value("${application.http-client.max-connections:50}")
    private int httpMaxConnections;

    @Value("${application.http-client.max-connections-per-route:20}")
    private int httpMaxConnectionsPerRoute;

    /** 管理后台密码（必须通过环境变量/配置注入，无默认值） */
    @Value("${application.admin-password:}")
    private String adminPassword;

    @Autowired(required = false)
    private ContextRefresher contextRefresher;

    @PostMapping("/auth")
    public ResponseEntity<Map<String, Object>> auth(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (adminPassword == null || adminPassword.isEmpty()) {
            result.put("success", false);
            result.put("message", "管理后台未配置密码，请通过环境变量 APPLICATION_ADMIN_PASSWORD 设置");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
        String password = body != null ? body.get("password") : null;
        if (password == null || password.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "密码不能为空");
            return ResponseEntity.badRequest().body(result);
        }
        if (constantTimeEquals(password, adminPassword)) {
            // 认证成功 → 创建服务端令牌
            String token = adminAuthFilter.createToken();
            result.put("success", true);
            result.put("message", "认证成功");
            result.put("token", token);
            return ResponseEntity.ok(result);
        }
        result.put("success", false);
        result.put("message", "密码错误");
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(result);
    }

    /**
     * 常量时间比较，避免时序攻击
     */
    private boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8),
                b.getBytes(StandardCharsets.UTF_8));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(@RequestHeader("X-Admin-Token") String token) {
        adminAuthFilter.removeToken(token);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "已退出登录");
        return ResponseEntity.ok(result);
    }

    /** 配置中需要脱敏的敏感键（匹配到即输出 ****） */
    private static final java.util.regex.Pattern SENSITIVE_KEYS = java.util.regex.Pattern.compile(
            "^(password|passwd|secret|token|api-key|api_key|access-key|access_key|cookie)$",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    @GetMapping("/config")
    public ResponseEntity<String> getConfig() {
        try {
            String yaml = configManagementService.getConfigAsYaml();
            return ResponseEntity.ok().contentType(MediaType.TEXT_PLAIN).body(redactSensitiveConfig(yaml));
        } catch (Exception e) {
            log.error("读取配置失败", e);
            return ResponseEntity.internalServerError().body("读取配置失败");
        }
    }

    /**
     * 对配置 YAML 中的敏感键值进行脱敏（密码/密钥/cookie 等输出为 ****），
     * 避免管理后台接口泄露明文凭据。
     */
    static String redactSensitiveConfig(String yaml) {
        if (yaml == null || yaml.isEmpty()) {
            return yaml;
        }
        StringBuilder sb = new StringBuilder();
        for (String line : yaml.split("\n", -1)) {
            String trimmed = line.trim();
            int colonIdx = trimmed.indexOf(':');
            if (colonIdx > 0) {
                String key = trimmed.substring(0, colonIdx).trim().replace("\"", "").replace("'", "");
                String value = trimmed.substring(colonIdx + 1).trim();
                if (!value.isEmpty() && !value.startsWith("#") && SENSITIVE_KEYS.matcher(key).matches()) {
                    String indent = line.substring(0, line.length() - line.stripLeading().length());
                    sb.append(indent).append(trimmed, 0, colonIdx + 1).append(" ****").append('\n');
                    continue;
                }
            }
            sb.append(line).append('\n');
        }
        return sb.toString();
    }

    @PutMapping("/config")
    public ResponseEntity<Map<String, Object>> updateConfig(@RequestBody String yamlContent) {
        try {
            configManagementService.saveConfigFromYaml(yamlContent);
            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("message", "配置已保存，可使用热重载或重启加载生效");
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "YAML 格式无效: " + e.getMessage());
            return ResponseEntity.badRequest().body(result);
        } catch (Exception e) {
            log.error("保存配置失败", e);
            Map<String, Object> result = new HashMap<>();
            result.put("success", false);
            result.put("message", "保存失败");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/restart")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> restart() {
        return deviceManagementService.restartProject()
            .thenApply(success -> {
                Map<String, Object> result = new HashMap<>();
                result.put("success", success);
                result.put("message", success ? "重启已触发" : "重启失败，请检查日志");
                result.put("timestamp", System.currentTimeMillis());
                if (success) {
                    return ResponseEntity.ok(result);
                }
                return ResponseEntity.internalServerError().body(result);
            });
    }

    @GetMapping("/monitor")
    public ResponseEntity<Map<String, Object>> monitor() {
        Map<String, Object> data = new LinkedHashMap<>();

        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("totalMemory", totalMemory);
        jvm.put("totalMemoryMB", totalMemory / 1024 / 1024);
        jvm.put("freeMemory", freeMemory);
        jvm.put("freeMemoryMB", freeMemory / 1024 / 1024);
        jvm.put("maxMemory", maxMemory);
        jvm.put("maxMemoryMB", maxMemory / 1024 / 1024);
        jvm.put("usedMemory", usedMemory);
        jvm.put("usedMemoryMB", usedMemory / 1024 / 1024);
        jvm.put("memoryUsagePercent", maxMemory > 0 ? (int) (usedMemory * 100 / maxMemory) : 0);
        jvm.put("availableProcessors", runtime.availableProcessors());
        jvm.put("uptime", ManagementFactory.getRuntimeMXBean().getUptime());
        double loadAvg = ManagementFactory.getOperatingSystemMXBean().getSystemLoadAverage();
        jvm.put("systemLoadAverage", loadAvg < 0 ? "N/A" : String.format("%.2f", loadAvg));
        data.put("jvm", jvm);

        Map<String, Object> threads = new LinkedHashMap<>();
        threads.put("activeCount", Thread.activeCount());
        ThreadGroup rootGroup = Thread.currentThread().getThreadGroup();
        while (rootGroup.getParent() != null) {
            rootGroup = rootGroup.getParent();
        }
        threads.put("totalCount", rootGroup.activeCount());
        data.put("threads", threads);

        data.put("devicePool", devicePoolService.getPoolStatus());

        if (fqEncryptWorker != null) {
            try {
                data.put("signatureCache", fqEncryptWorker.getCacheStats());
            } catch (Exception e) {
                data.put("signatureCache", Map.of("error", e.getMessage()));
            }
        }

        Map<String, Object> redisInfo = new LinkedHashMap<>();
        try {
            if (redisTemplate != null && redisTemplate.getConnectionFactory() != null) {
                String ping = redisTemplate.getConnectionFactory().getConnection().ping();
                redisInfo.put("connected", true);
                redisInfo.put("ping", ping);
            } else {
                redisInfo.put("connected", false);
                redisInfo.put("message", "Redis 未配置或不可用");
            }
        } catch (Exception e) {
            redisInfo.put("connected", false);
            redisInfo.put("error", e.getMessage());
        }
        data.put("redis", redisInfo);

        Map<String, Object> httpClient = new LinkedHashMap<>();
        httpClient.put("connectTimeoutMs", httpConnectTimeoutMs);
        httpClient.put("readTimeoutMs", httpReadTimeoutMs);
        httpClient.put("maxConnections", httpMaxConnections);
        httpClient.put("maxConnectionsPerRoute", httpMaxConnectionsPerRoute);
        data.put("httpClient", httpClient);

        Map<String, Object> disk = new LinkedHashMap<>();
        try {
            File root = new File(".");
            long totalSpace = root.getTotalSpace();
            long freeSpace = root.getFreeSpace();
            long usableSpace = root.getUsableSpace();
            disk.put("totalSpaceMB", totalSpace / 1024 / 1024);
            disk.put("freeSpaceMB", freeSpace / 1024 / 1024);
            disk.put("usableSpaceMB", usableSpace / 1024 / 1024);
            disk.put("usedPercent", totalSpace > 0 ? (int)((totalSpace - freeSpace) * 100 / totalSpace) : 0);
        } catch (Exception e) {
            disk.put("error", e.getMessage());
        }
        data.put("disk", disk);

        return ResponseEntity.ok(data);
    }

    @GetMapping("/device-pool")
    public ResponseEntity<Map<String, Object>> devicePoolStatus() {
        return ResponseEntity.ok(devicePoolService.getPoolStatus());
    }

    @PostMapping("/device-pool/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildDevicePool() {
        devicePoolService.rebuildPool();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("message", "设备池已重建");
        result.put("status", devicePoolService.getPoolStatus());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> status = new HashMap<>();
        status.put("status", "UP");
        status.put("service", "Admin Management");
        status.put("timestamp", System.currentTimeMillis());
        return status;
    }

    @GetMapping(path = "", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<Void> adminPage() {
        return ResponseEntity.status(HttpStatus.FOUND)
            .header(HttpHeaders.LOCATION, "/admin/login.html")
            .build();
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshConfig() {
        Map<String, Object> result = new LinkedHashMap<>();
        if (contextRefresher == null) {
            result.put("success", false);
            result.put("message", "ContextRefresher 不可用，请检查 spring-cloud-context 依赖");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(result);
        }
        try {
            Set<String> changedKeys = contextRefresher.refresh();
            result.put("success", true);
            result.put("message", "配置已热重载");
            result.put("changedKeys", changedKeys);
            result.put("timestamp", System.currentTimeMillis());
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("热重载失败", e);
            result.put("success", false);
            result.put("message", "热重载失败");
            return ResponseEntity.internalServerError().body(result);
        }
    }

    @PostMapping("/device-pool/remove")
    public ResponseEntity<Map<String, Object>> removeDevice(@RequestParam String deviceId) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (deviceId == null || deviceId.trim().isEmpty()) {
            result.put("success", false);
            result.put("message", "deviceId 不能为空");
            return ResponseEntity.badRequest().body(result);
        }
        boolean removed = devicePoolService.removeDeviceById(deviceId.trim());
        result.put("success", removed);
        result.put("message", removed ? "设备已移除" : "未找到设备或设备池未启用");
        if (removed) {
            result.put("status", devicePoolService.getPoolStatus());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/device-pool/add")
    public ResponseEntity<Map<String, Object>> addDevice() {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean added = devicePoolService.addDevice();
        result.put("success", added);
        result.put("message", added ? "设备已添加" : "设备池已满或添加失败");
        result.put("status", devicePoolService.getPoolStatus());
        return ResponseEntity.ok(result);
    }
}
