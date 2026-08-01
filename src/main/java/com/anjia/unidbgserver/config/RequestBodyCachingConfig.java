package com.anjia.unidbgserver.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 请求体缓存过滤器
 *
 * 问题背景：LoggingAspect 通过 AOP 在 Controller 方法执行前读取
 * request.getReader()，但 Spring MVC 的参数绑定（读取 @RequestBody）
 * 发生在 AOP 代理调用之前，此时请求流已被消费，readBody() 永远拿到空串
 * （TOKEN_PATTERN 脱敏逻辑从未生效）。
 *
 * 解决方案：用 ContentCachingRequestWrapper 包装请求，让 body 被缓存后
 * 可重复读取。LoggingAspect 通过 RequestContextHolder 获取包装实例读取 body。
 */
@Configuration
public class RequestBodyCachingConfig {

    @Bean
    public FilterRegistrationBean<ContentCachingRequestFilter> contentCachingRequestFilter() {
        FilterRegistrationBean<ContentCachingRequestFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new ContentCachingRequestFilter());
        registration.addUrlPatterns("/api/*");
        registration.setName("contentCachingRequestFilter");
        registration.setOrder(1); // 在 AdminAuthFilter 之后、Controller 之前
        return registration;
    }

    public static class ContentCachingRequestFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                                        HttpServletResponse response,
                                        FilterChain filterChain) throws ServletException, IOException {
            if (request instanceof ContentCachingRequestWrapper) {
                filterChain.doFilter(request, response);
                return;
            }
            ContentCachingRequestWrapper wrapper = new ContentCachingRequestWrapper(request, 4096);
            filterChain.doFilter(wrapper, response);
        }
    }
}
