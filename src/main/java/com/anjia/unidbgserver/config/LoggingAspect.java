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

import javax.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.util.regex.Pattern;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"(token|password|secret)\"\\s*:\\s*\"[^\"]*\"");
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
            requestURI.startsWith("/api/fullbook/download") ||
            requestURI.startsWith("/api/img/proxy")) {
            return joinPoint.proceed();
        }

        String queryString = request.getQueryString();
        String path = queryString != null ? requestURI + "?" + queryString : requestURI;
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
            body = readBody(request);
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
                log.warn("[SLOW REQUEST] PATH={} DURATION={}ms STATUS=200", path, duration);
            } else {
                log.info("[RESPONSE] PATH={} DURATION={}ms STATUS=200", path, duration);
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
        log.error("[EXCEPTION] {}.{}() - {}: {}",
            joinPoint.getSignature().getDeclaringTypeName(),
            joinPoint.getSignature().getName(),
            ex.getClass().getSimpleName(),
            ex.getMessage());
    }

    private String readBody(HttpServletRequest request) {
        try (BufferedReader reader = request.getReader()) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }
}
