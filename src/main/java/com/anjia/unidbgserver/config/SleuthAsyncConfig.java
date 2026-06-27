package com.anjia.unidbgserver.config;

import org.springframework.beans.factory.BeanFactory;
import org.springframework.cloud.sleuth.instrument.async.LazyTraceExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.Executor;

/**
 * Sleuth 异步上下文传播配置。
 * bizExecutor 已改用 ThreadPoolTaskExecutor，Sleuth LazyTraceAsyncCustomizer 会将其自动包装；
 * 此处显式注册 LazyTraceExecutor 作为备用。
 */
@Configuration
public class SleuthAsyncConfig {

    @Bean("traceableExecutor")
    public Executor traceableExecutor(BeanFactory beanFactory, Executor bizExecutor) {
        return new LazyTraceExecutor(beanFactory, bizExecutor);
    }
}
