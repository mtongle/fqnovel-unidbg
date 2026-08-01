package com.anjia.unidbgserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionHandler;

/**
 * 异步任务线程池配置
 *
 * 注意：yml 中 spring.task.execution.pool.* 是线程池的权威配置，
 * 这里不再用 Math.max(corePoolSize, cores*2+1) 覆盖用户配置（原实现导致
 * 配置在多数机器上不生效）。
 */
@Slf4j
@Configuration
public class AsyncConfig {

    @Value("${spring.task.execution.pool.core-size:8}")
    private int corePoolSize;

    @Value("${spring.task.execution.pool.max-size:16}")
    private int maxPoolSize;

    @Value("${spring.task.execution.pool.queue-capacity:1000}")
    private int queueCapacity;

    @Bean("bizExecutor")
    public Executor bizExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setKeepAliveSeconds(60);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("biz-pool-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        // 队列满时由调用线程执行任务（降级而非丢弃，避免下载/评论任务静默丢失）
        executor.setRejectedExecutionHandler(callerRunsPolicy());
        executor.initialize();
        log.info("bizExecutor 初始化完成: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
        return executor;
    }

    private RejectedExecutionHandler callerRunsPolicy() {
        return (r, exec) -> {
            log.warn("bizExecutor 队列已满，任务将由调用线程执行（CallerRunsPolicy）: active={}, max={}",
                    exec.getActiveCount(), maxPoolSize);
            r.run();
        };
    }
}
