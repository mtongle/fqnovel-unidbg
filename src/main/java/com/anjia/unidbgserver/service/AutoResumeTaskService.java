package com.anjia.unidbgserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 自动恢复任务服务
 * 定时检查未完成的下载任务并自动恢复
 */
@Slf4j
@Service
public class AutoResumeTaskService {

    @Autowired
    private FullBookDownloadService fullBookDownloadService;

    /**
     * 定时检查未完成的任务（每30分钟执行一次）
     *
     * 使用 fixedDelay 而非 fixedRate：上一次全量恢复可能超过 30 分钟
     * （大书多本），fixedRate 会导致任务重叠执行。
     */
    @Scheduled(fixedDelay = 30 * 60 * 1000) // 30分钟（上次执行结束后再等30分钟）
    public void scheduledAutoResume() {
        log.info("开始定时检查未完成的下载任务");

        // autoResumeAllDownloads 本身已返回 CompletableFuture（在 bizExecutor 中执行），
        // 无需再包一层 runAsync 消耗公共 ForkJoinPool
        fullBookDownloadService.autoResumeAllDownloads()
            .thenAccept(result -> {
                if (result.isSuccess()) {
                    log.info("定时自动恢复任务完成 - {}", result.getMessage());
                } else {
                    log.error("定时自动恢复任务失败 - {}", result.getMessage());
                }
            })
            .exceptionally(e -> {
                log.error("定时自动恢复任务执行失败", e);
                return null;
            });
    }

    /**
     * 手动触发自动恢复检查
     */
    public CompletableFuture<FullBookDownloadService.AutoResumeAllResult> triggerAutoResume() {
        log.info("手动触发自动恢复检查");
        return fullBookDownloadService.autoResumeAllDownloads();
    }
}
