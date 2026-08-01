package com.anjia.unidbgserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

/**
 * Jackson 配置
 *
 * 通过 Jackson2ObjectMapperBuilder 定制而非 new ObjectMapper()，
 * 保留 Spring Boot 自动配置注册的 JavaTimeModule（java.time 序列化）
 * 与 spring.jackson.* 配置绑定，避免破坏 Boot 的 Jackson 自动装配。
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper(Jackson2ObjectMapperBuilder builder) {
        // 忽略未知属性（API 响应字段多于 DTO 时静默跳过）
        return builder
                .featuresToDisable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .build();
    }

    /**
     * 兜底：让 Spring MVC 的 MappingJackson2HttpMessageConverter 也使用同一份配置
     * （正常情况下 Boot 会自动装配，此 Bean 仅为显式声明）
     */
    @Bean
    @Primary
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter(Jackson2ObjectMapperBuilder builder) {
        return new MappingJackson2HttpMessageConverter(builder.build());
    }
}
