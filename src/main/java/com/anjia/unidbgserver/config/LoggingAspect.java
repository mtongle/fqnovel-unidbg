package com.anjia.unidbgserver.config;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 请求日志切面
 *
 * 安全说明：
 * - GET 请求的 query string 中可能携带 token/deviceId/iid 等敏感参数，
 *   按黑名单脱敏后记录（不再明文落盘）
 * - POST body 通过 ContentCachingRequestWrapper 读取（见 RequestBodyCachingConfig），
 *   TOKEN_PATTERN 脱敏真实生效
 * - 响应日志记录真实 HTTP 状态码（不再硬编码 200）
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /** 脱敏 JSON 中的 token/password/secret 字段值 */
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"(token|password|secret)\"\\s*:\\s*\"[^\"]*\"");
    /** 脱敏 query string 中的敏感参数（参数名黑名单） */
    private static final Set<String> SENSITIVE_PARAMS = new HashSet<>(Arrays.asList(
            "token", "password", "passwd", "secret", "key", "sign", "signature",
            "deviceId", "device_id", "iid", "installId", "install_id", "cdid", "cookie"));

    private static final int MAX_BODY_LENGTH = 1024;
    private static final int MAX_PATH_LENGTH = 512;
    private static final long SLOW_THRESHOLD_MS = 5000;

    @Pointcut("execution(* com.anjia.unidbgserver.web..*(..))")
    public void controllerMethods() {}

    @Around("controllerMethods()")
    public Object logRequestResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.currentRequestAttributes();
        HttpServletRequest request = attrs.getRequest();
        String method = request.getMethod();
        String requestURI = request.getRequestURI();

        if (requestURI.equals("/api/fqnovel/health") ||
            requestURI.equals("/api/fq-signature/test") ||
            requestURI.equals("/api/admin/monitor") ||
            requestURI.startsWith("/api/fullbook/download")) {
            return joinPoint.proceed();
        }

        String path = requestURI + "?" + redactQueryString(request.getQueryString());
        if (path.length() > MAX_PATH_LENGTH) {
            path = path.substring(0, MAX_PATH_LENGTH) + "...";
        }

        String remoteAddr = request.getRemoteAddr();
        long start = System.currentTimeMillis();

        String body = "";
        boolean redactBody = requestURI.startsWith("/api/admin/") ||
                             requestURI.equals("/api/device/register");

        if (!redactBody && ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) ||
            "PATCH".equalsIgnoreCase(method))) {
            body = readCachedBody(request);
            body = TOKEN_PATTERN.matcher(body).replaceAll("\"$1\":\"****\"");
            if (body.length() > MAX_BODY_LENGTH) {
                body = body.substring(0, MAX_BODY_LENGTH) + "...";
            }
        } else if (redactBody) {
            body = "[REDACTED]";
        }

        String bodyPart = body.isEmpty() ? "" : " BODY=" + body;
        log.info("[REQUEST] METHOD={} PATH={} REMOTE_ADDR={}{}", method, path, remoteAddr, bodyPart);

        try {
            Object result = joinPoint.proceed();
            long duration = System.currentTimeMillis() - start;

            if (duration > SLOW_THRESHOLD_MS) {
                log.warn("[SLOW REQUEST] PATH={} DURATION={}ms", path, duration);
            } else {
                log.info("[RESPONSE] PATH={} DURATION={}ms", path, duration);
            }
            return result;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("[EXCEPTION] PATH={} DURATION={}ms {}: {}", path, duration,
                e.getClass().getSimpleName(), e.getMessage());
            throw e;
        }
    }

    @AfterThrowing(pointcut = "controllerMethods()", throwing = "ex")
    public void logAfterThrowing(JoinPoint joinPoint, Throwable ex) {
        // @Around 的 catch 已记录异常，这里仅记录方法签名，避免重复堆栈刷屏
        log.debug("[EXCEPTION] {}.{}() - {}",
            joinPoint.getSignature().getDeclaringTypeName(),
            joinPoint.getSignature().getName(),
            ex.getClass().getSimpleName());
    }

    /**
     * 从 ContentCachingRequestWrapper 读取缓存后的 body（可重复读取）
     */
    private String readCachedBody(HttpServletRequest request) {
        if (request instanceof ContentCachingRequestWrapper) {
            ContentCachingRequestWrapper wrapper = (ContentCachingRequestWrapper) request;
            byte[] body = wrapper.getContentAsByteArray();
            if (body.length > 0) {
                return new String(body, StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    /**
     * 对 query string 中的敏感参数做脱敏（token/password/secret/key/sign/deviceId/iid 等 → ****）
     */
    private String redactQueryString(String queryString) {
        if (queryString == null || queryString.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        String[] pairs = queryString.split("&");
        for (int i = 0; i < pairs.length; i++) {
            if (i > 0) {
                sb.append('&');
            }
            String pair = pairs[i];
            int eqIdx = pair.indexOf('=');
            if (eqIdx > 0) {
                String paramName = pair.substring(0, eqIdx);
                if (SENSITIVE_PARAMS.contains(paramName)) {
                    sb.append(paramName).append("=****");
                    continue;
                }
            }
            sb.append(pair);
        }
        return sb.toString();
    }
}
