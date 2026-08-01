package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.service.FQEncryptServiceWorker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping(path = "/api/fq-signature", produces = MediaType.APPLICATION_JSON_VALUE)
public class FQEncryptController {

    @Resource(name = "fqEncryptWorker")
    private FQEncryptServiceWorker fqSignatureServiceWorker;

    /**
     * 生成FQ应用的签名headers
     * @param request 包含 url 和 headers 的请求体
     * @return 包含各种签名header的结果
     */
    @PostMapping("generateSignature")
    public CompletableFuture<Map<String, String>> generateSignature(@RequestBody Map<String, String> request) {
        String url = request.get("url");
        String headers = request.get("headers");

        // 检查必需的参数
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL参数不能为空");
        }

        // headers可以为空，默认为空字符串
        if (headers == null) {
            headers = "";
        }

        log.debug("接收到FQ签名请求 - URL: {}", url);

        // 返回 CompletableFuture，由 Spring 异步完成，不阻塞 servlet 线程
        return fqSignatureServiceWorker.generateSignatureHeaders(url, headers);
    }

    /**
     * 生成FQ应用的签名headers (支持Map格式的headers)
     * @param request 包含 url 和 headerMap 的请求体
     * @return 包含各种签名header的结果
     */
    @SuppressWarnings("unchecked")
    @PostMapping("generateSignatureWithMap")
    public CompletableFuture<Map<String, String>> generateSignatureWithMap(@RequestBody Map<String, Object> request) {
        String url = request.get("url") != null ? request.get("url").toString() : null;

        // 防御性校验：headerMap 必须是 Map，避免 ClassCastException
        Map<String, String> headerMap = null;
        Object rawHeaderMap = request.get("headerMap");
        if (rawHeaderMap instanceof Map) {
            headerMap = (Map<String, String>) rawHeaderMap;
        } else if (rawHeaderMap != null) {
            throw new IllegalArgumentException("headerMap 必须是 JSON 对象");
        }

        // 检查必需的参数
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL参数不能为空");
        }

        log.debug("接收到FQ签名请求(Map格式) - URL: {}", url);

        return fqSignatureServiceWorker.generateSignatureHeaders(url, headerMap);
    }

    /**
     * 简化版签名生成，只需要URL
     * @param request 包含 url 的请求体
     * @return 包含各种签名header的结果
     */
    @PostMapping("generateSignatureSimple")
    public CompletableFuture<Map<String, String>> generateSignatureSimple(@RequestBody Map<String, String> request) {
        String url = request.get("url");

        // 检查必需的参数
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL参数不能为空");
        }

        log.debug("接收到FQ简化签名请求 - URL: {}", url);

        return fqSignatureServiceWorker.generateSignatureHeaders(url, "");
    }

    /**
     * GET方式的签名生成接口（用于简单测试）
     * @param url 请求的URL
     * @return 包含各种签名header的结果
     */
    @GetMapping("test")
    public CompletableFuture<Map<String, String>> testSignature(@RequestParam String url) {
        // 检查必需的参数
        if (url == null || url.trim().isEmpty()) {
            throw new IllegalArgumentException("URL参数不能为空");
        }

        log.debug("接收到FQ测试签名请求 - URL: {}", url);

        return fqSignatureServiceWorker.generateSignatureHeaders(url, "");
    }

    /**
     * 健康检查接口
     * @return 服务状态
     */
    @GetMapping("health")
    public Map<String, Object> health() {
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("status", "UP");
        healthStatus.put("service", "FQ Signature Service");
        healthStatus.put("timestamp", System.currentTimeMillis());
        return healthStatus;
    }
}
