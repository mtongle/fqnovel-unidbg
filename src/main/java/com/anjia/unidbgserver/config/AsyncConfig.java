package com.anjia.unidbgserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig {

    @Value("${spring.task.execution.pool.core-size:8}")
    private int corePoolSize;

    @Value("${spring.task.execution.pool.max-size:16}")
    private int maxPoolSize;

    @Value("${spring.task.execution.pool.queue-capacity:1000}")
    private int queueCapacity;

    @Bean("bizExecutor")
    public Executor bizExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int actualCore = Math.max(corePoolSize, cores * 2 + 1);
        int actualMax = Math.max(maxPoolSize, actualCore * 2);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(actualCore);
        executor.setMaxPoolSize(actualMax);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("biz-pool-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setRejectedExecutionHandler((r, exec) -> {
            log.warn("bizExecutor 任务被拒绝：队列已满({}), 活动线程: {}, 最大线程: {}",
                queueCapacity, exec.getActiveCount(), actualMax);
        });
        executor.initialize();
        return executor;
    }
}
