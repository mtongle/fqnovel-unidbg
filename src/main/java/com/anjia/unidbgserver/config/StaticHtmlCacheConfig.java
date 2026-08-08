package com.anjia.unidbgserver.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * HTML 页面静态响应使用 no-cache（重验证），保证面板去重、配置编辑器等页面改动及时生效；
 * CSS/JS/图片等资产仍走 application.yml 中 spring.web.resources.cache.cachecontrol 的 7 天缓存。
 * 注意：资源处理器使用 PathPattern（非 Ant），`**` 之后不能再跟内容，故不能写 /**\/*.html；
 * 本项目静态 HTML 只分布在 根目录 / 、/admin/ 与 /error/ 三层，分别注册更具体的模式覆盖默认的 /**。
 */
@Configuration
public class StaticHtmlCacheConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 注意：资源解析取的是“模式内路径”（* 捕获段），再拼接到 addResourceLocations 目录上，
        // 因此每个模式必须指向其文件实际所在目录，否则 /admin/*.html 会去根目录找 search.html 之类。
        registry.addResourceHandler("/*.html")
                .addResourceLocations("classpath:/static/", "classpath:/public/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/admin/*.html")
                .addResourceLocations("classpath:/static/admin/")
                .setCacheControl(CacheControl.noCache());
        registry.addResourceHandler("/error/*.html")
                .addResourceLocations("classpath:/static/error/")
                .setCacheControl(CacheControl.noCache());
    }
}
