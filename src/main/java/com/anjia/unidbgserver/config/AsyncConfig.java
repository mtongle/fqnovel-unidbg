package com.anjia.unidbgserver.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
    public ExecutorService bizExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        int actualCore = Math.max(corePoolSize, cores * 2 + 1);
        int actualMax = Math.max(maxPoolSize, actualCore * 2);

        return new ThreadPoolExecutor(
            actualCore,
            actualMax,
            60L,
            TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(queueCapacity),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger(1);
                @Override
                public Thread newThread(Runnable r) {
                    Thread t = new Thread(r, "biz-pool-" + counter.getAndIncrement());
                    t.setDaemon(true);
                    return t;
                }
            },
            (r, executor) -> {
                // 队列满且线程池满载时，丢弃任务并记录警告
                // 调用方的 CompletableFuture 会通过 exceptionally 处理超时/异常
                log.warn("bizExecutor 任务被拒绝：队列已满({}), 活动线程: {}, 最大线程: {}",
                    queueCapacity, executor.getActiveCount(), actualMax);
            }
        );
    }
}
