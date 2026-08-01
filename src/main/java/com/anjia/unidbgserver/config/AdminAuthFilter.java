package com.anjia.unidbgserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理后台认证过滤器
 *
 * 拦截所有 /api/admin/* 请求（除 /auth 和 /health 外），
 * 校验请求头 X-Admin-Token 是否为有效令牌。
 *
 * 修复漏洞：API 未授权访问（CTF 报告 漏洞2）
 * 原本所有 /api/admin/* 接口无任何认证拦截，此过滤器强制要求令牌验证。
 *
 * 安全加固：
 * - URL 解码后再做前缀匹配，防止 /api/%61dmin/* 编码绕过
 * - fail-closed：匹配到的管理路径一律校验令牌（无默认放行）
 * - 令牌带 24 小时 TTL，过期自动清理（与前端 admin-common.js 的 AUTH_TTL_MS 一致）
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminAuthFilter implements Filter {

    /** 令牌有效期：24 小时 */
    private static final long TOKEN_TTL_MS = 24 * 60 * 60 * 1000L;

    /** 无需令牌的公开路径（认证接口与健康检查） */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/admin/auth",
            "/api/admin/health"
    );

    /** 有效令牌集合：token -> 过期时间戳 */
    private final Map<String, Long> validTokens = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        // 使用解码后的路径匹配，防止 URL 编码绕过（如 /api/%61dmin/config）
        String path = request.getRequestURI();
        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);

        // 仅拦截 /api/admin 路径（含精确匹配 /api/admin）
        if (!(decodedPath.equals("/api/admin") || decodedPath.startsWith("/api/admin/"))) {
            chain.doFilter(request, response);
            return;
        }

        // 放行认证接口、健康检查、登录页跳转（无需令牌）
        if (PUBLIC_PATHS.contains(decodedPath) || decodedPath.equals("/api/admin")) {
            chain.doFilter(request, response);
            return;
        }

        // 校验令牌（fail-closed：判断失误时拦截而非放行）
        String token = request.getHeader("X-Admin-Token");
        if (token != null && isTokenValid(token)) {
            chain.doFilter(request, response);
            return;
        }

        // 未授权 → 返回 401
        log.warn("未授权访问被拦截: {} from {}", path, request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"未授权访问，请先登录\"}");
    }

    private boolean isTokenValid(String token) {
        Long expireAt = validTokens.get(token);
        if (expireAt == null) {
            return false;
        }
        if (System.currentTimeMillis() > expireAt) {
            validTokens.remove(token);
            return false;
        }
        return true;
    }

    /**
     * 创建新的管理后台令牌（24 小时有效）
     */
    public String createToken() {
        String token = java.util.UUID.randomUUID().toString();
        validTokens.put(token, System.currentTimeMillis() + TOKEN_TTL_MS);
        log.debug("管理后台令牌已创建，当前有效令牌数: {}", validTokens.size());
        return token;
    }

    /**
     * 移除指定的管理后台令牌（登出）
     */
    public void removeToken(String token) {
        if (token != null && validTokens.remove(token) != null) {
            log.debug("管理后台令牌已移除，当前有效令牌数: {}", validTokens.size());
        }
    }

    /**
     * 获取当前有效令牌数量（用于监控）
     */
    public int getActiveTokenCount() {
        // 顺带清理过期令牌
        long now = System.currentTimeMillis();
        validTokens.entrySet().removeIf(entry -> entry.getValue() < now);
        return validTokens.size();
    }
}
