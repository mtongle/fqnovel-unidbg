package com.anjia.unidbgserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminAuthFilter implements Filter {

    /**
     * 有效令牌集合（内存存储，应用重启后失效）
     * 生产环境建议使用 Redis 替代
     */
    private final Set<String> validTokens = ConcurrentHashMap.newKeySet();

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        String path = request.getRequestURI();

        // 仅拦截 /api/admin/* 路径
        if (!path.startsWith("/api/admin")) {
            chain.doFilter(request, response);
            return;
        }

        // 放行认证接口和健康检查（无需令牌）
        if (path.equals("/api/admin/auth") || path.equals("/api/admin/health")) {
            chain.doFilter(request, response);
            return;
        }

        // 校验令牌
        String token = request.getHeader("X-Admin-Token");
        if (token != null && validTokens.contains(token)) {
            chain.doFilter(request, response);
            return;
        }

        // 未授权 → 返回 401
        log.warn("未授权访问被拦截: {} from {}", path, request.getRemoteAddr());
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"success\":false,\"message\":\"未授权访问，请先登录\"}");
    }

    /**
     * 创建新的管理后台令牌
     */
    public String createToken() {
        String token = java.util.UUID.randomUUID().toString();
        validTokens.add(token);
        log.debug("管理后台令牌已创建: {}", token);
        return token;
    }

    /**
     * 移除指定的管理后台令牌（登出）
     */
    public void removeToken(String token) {
        if (token != null && validTokens.remove(token)) {
            log.debug("管理后台令牌已移除: {}", token);
        }
    }

    /**
     * 获取当前有效令牌数量（用于监控）
     */
    public int getActiveTokenCount() {
        return validTokens.size();
    }
}
