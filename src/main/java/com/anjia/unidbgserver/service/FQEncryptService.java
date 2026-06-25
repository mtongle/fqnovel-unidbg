package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.UnidbgProperties;
import com.anjia.unidbgserver.unidbg.IdleFQ;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class FQEncryptService {

    private final Object engineLock = new Object();
    private final boolean verbose;
    private volatile IdleFQ idleFQ;

    public FQEncryptService(UnidbgProperties properties) {
        this.verbose = properties.isVerbose();
        try {
            this.idleFQ = new IdleFQ(this.verbose);
            log.info("FQ签名服务初始化完成");
        } catch (Exception e) {
            log.error("FQ签名服务初始化失败，引擎暂不可用", e);
            this.idleFQ = null;
        }
    }

    /**
     * 生成FQ应用的签名headers
     *
     * @param url 请求的URL
     * @param headers 请求头信息，格式为key\r\nvalue\r\n的字符串
     * @return 包含各种签名header的Map
     */
    public Map<String, String> generateSignatureHeaders(String url, String headers) {
        synchronized (engineLock) {
            try {
                log.debug("准备生成FQ签名 - URL: {}", url);

                IdleFQ currentEngine = idleFQ;
                if (currentEngine == null) {
                    log.error("签名引擎未初始化");
                    return createErrorResponse("签名引擎未初始化");
                }

                String signatureResult = currentEngine.generateSignature(url, headers);

                if (signatureResult == null || signatureResult.isEmpty()) {
                    log.error("签名生成失败，返回结果为空");
                    return createErrorResponse("签名生成失败");
                }

                Map<String, String> result = parseSignatureResult(signatureResult);
                result.remove("X-Neptune");

                log.debug("FQ签名生成成功: {}", result);
                return result;

            } catch (Exception e) {
                log.error("生成FQ签名失败", e);
                return createErrorResponse("生成失败: " + e.getMessage());
            }
        }
    }

    /**
     * 生成FQ应用的签名headers (重载方法，支持Map格式的headers)
     *
     * @param url 请求的URL
     * @param headerMap 请求头的Map，key为header名称，value为header值
     * @return 包含各种签名header的Map
     */
    public Map<String, String> generateSignatureHeaders(String url, Map<String, String> headerMap) {
        if (headerMap == null || headerMap.isEmpty()) {
            return generateSignatureHeaders(url, "");
        }

        // 将Map转换为\r\n分隔的字符串格式
        StringBuilder headerBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            headerBuilder.append(entry.getKey()).append("\r\n")
                .append(entry.getValue()).append("\r\n");
        }

        // 移除最后的\r\n
        String headers = headerBuilder.toString();
        if (headers.endsWith("\r\n")) {
            headers = headers.substring(0, headers.length() - 2);
        }

        return generateSignatureHeaders(url, headers);
    }

    /**
     * 解析签名生成结果
     * 根据返回的字符串解析出各个header值
     */
    private Map<String, String> parseSignatureResult(String signatureResult) {
        Map<String, String> result = new HashMap<>();

        try {
            // 根据实际返回格式解析，这里假设返回的是JSON格式或特定分隔符格式
            // 需要根据实际的signatureResult格式来调整解析逻辑

            // 如果是JSON格式，可以使用JSON解析
            // 如果是特定格式，需要按照格式解析

            // 示例：假设返回格式包含各个header信息
            // 这里需要根据实际的IdleFQ.generateSignature返回格式来实现

            // 临时实现：假设返回的是完整的header字符串
            String[] lines = signatureResult.split("\n");
            for (int i = 0; i < lines.length - 1; i += 2) {
                if (i + 1 < lines.length) {
                    String key = lines[i].trim();
                    String value = lines[i + 1].trim();
                    result.put(key, value);
                }
            }

            // 确保包含预期的header
            if (!result.containsKey("X-Argus")) {
                // 如果解析失败，返回默认值或错误
                log.warn("解析结果中未找到X-Argus header");
            }

        } catch (Exception e) {
            log.error("解析签名结果失败", e);
            return createErrorResponse("解析失败: " + e.getMessage());
        }

        return result;
    }

    /**
     * 创建错误响应
     */
    private Map<String, String> createErrorResponse(String errorMessage) {
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", errorMessage);
        return errorResponse;
    }

    /**
     * 重置签名引擎实例
     */
    public void reset() {
        synchronized (engineLock) {
            if (idleFQ != null) {
                try {
                    idleFQ.destroy();
                } catch (Exception e) {
                    log.warn("销毁旧签名引擎失败，继续重建", e);
                }
            }

            // 尝试初始化，失败后重试一次
            Exception lastError = null;
            for (int i = 0; i < 2; i++) {
                try {
                    idleFQ = new IdleFQ(verbose);
                    log.info("FQ签名服务重置完成");
                    return;
                } catch (Exception e) {
                    lastError = e;
                    log.warn("FQ签名服务重置失败(第{}次)，短暂延迟后重试", i + 1);
                    try { Thread.sleep(1000); } catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
                }
            }

            log.error("FQ签名服务重置失败，引擎暂不可用", lastError);
            idleFQ = null;
        }
    }

    /**
     * 清理资源
     *
     * 注意：不清除 TempFileUtils 缓存。临时文件是静态共享的，
     * 由 deleteOnExit() 在 JVM 退出时自动清理，reset 时如果清空
     * 缓存会导致新 IdleFQ 实例初始化时无法复用已提取的临时文件。
     */
    public void destroy() {
        synchronized (engineLock) {
            if (idleFQ != null) {
                idleFQ.destroy();
                idleFQ = null;
            }

            log.info("FQ签名服务资源释放完成");
        }
    }
}
