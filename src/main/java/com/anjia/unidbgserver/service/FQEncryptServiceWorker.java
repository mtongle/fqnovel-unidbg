package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.config.UnidbgProperties;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.github.unidbg.worker.Worker;
import com.github.unidbg.worker.WorkerPool;
import com.github.unidbg.worker.WorkerPoolFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service("fqEncryptWorker")
public class FQEncryptServiceWorker extends Worker {

    private UnidbgProperties unidbgProperties;
    private volatile WorkerPool pool;
    private FQEncryptService fqEncryptService;
    private int poolSize = 4;
    private final Object resetLock = new Object();

    @Autowired
    private SignatureCacheService signatureCacheService;

    @Autowired
    public void init(UnidbgProperties unidbgProperties) {
        this.unidbgProperties = unidbgProperties;
    }

    public FQEncryptServiceWorker() {
        super(WorkerPoolFactory.create(FQEncryptServiceWorker::new, Runtime.getRuntime().availableProcessors()));
    }

    public FQEncryptServiceWorker(WorkerPool pool) {
        super(pool);
    }

    @Autowired
    public FQEncryptServiceWorker(UnidbgProperties unidbgProperties,
                                    @Value("${spring.task.execution.pool.core-size:4}") int poolSize) {
        super(WorkerPoolFactory.create(FQEncryptServiceWorker::new, Runtime.getRuntime().availableProcessors()));
        this.unidbgProperties = unidbgProperties;
        if (this.unidbgProperties.isAsync()) {
            this.poolSize = Math.max(poolSize, 4);
            pool = createAsyncPool();
            log.info("FQ签名服务线程池池大小为:{}", this.poolSize);
        } else {
            this.fqEncryptService = new FQEncryptService(unidbgProperties);
        }
    }

    public FQEncryptServiceWorker(boolean dynarmic, boolean verbose, WorkerPool pool) {
        super(pool);
        this.unidbgProperties = new UnidbgProperties();
        unidbgProperties.setDynarmic(dynarmic);
        unidbgProperties.setVerbose(verbose);
        log.info("FQ签名服务 - 是否启用动态引擎:{}, 是否打印详细信息:{}", dynarmic, verbose);
        this.fqEncryptService = new FQEncryptService(unidbgProperties);
    }

    public CompletableFuture<Map<String, String>> generateSignatureHeaders(String url, String headers) {
        String cacheKey = buildCacheKey(url, headers);
        Map<String, String> cached = signatureCacheService.getSignature(cacheKey);
        if (cached != null) {
            log.debug("签名缓存命中: {}", url);
            return CompletableFuture.completedFuture(cached);
        }

        FQEncryptServiceWorker worker;
        Map<String, String> result;

        if (this.unidbgProperties.isAsync()) {
            WorkerPool currentPool = pool;
            if (currentPool == null) {
                throw new IllegalStateException("FQ签名线程池不可用");
            }
            while (true) {
                if ((worker = currentPool.borrow(2, TimeUnit.SECONDS)) == null) {
                    continue;
                }
                result = worker.doWork(url, headers);
                currentPool.release(worker);
                break;
            }
        } else {
            synchronized (this) {
                result = this.doWork(url, headers);
            }
        }

        signatureCacheService.putSignature(cacheKey, result);
        return CompletableFuture.completedFuture(result);
    }

    @org.springframework.scheduling.annotation.Async
    public CompletableFuture<Map<String, String>> generateSignatureHeadersAsync(String url, String headers) {
        return generateSignatureHeaders(url, headers);
    }

    public CompletableFuture<Map<String, String>> generateSignatureHeaders(String url, Map<String, String> headerMap) {
        if (headerMap == null || headerMap.isEmpty()) {
            return generateSignatureHeaders(url, "");
        }

        StringBuilder headerBuilder = new StringBuilder();
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            headerBuilder.append(entry.getKey()).append("\r\n")
                .append(entry.getValue()).append("\r\n");
        }

        String headersStr = headerBuilder.toString();
        if (headersStr.endsWith("\r\n")) {
            headersStr = headersStr.substring(0, headersStr.length() - 2);
        }

        return generateSignatureHeaders(url, headersStr);
    }

    private String buildCacheKey(String url, String headers) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            md.update(url.getBytes("UTF-8"));
            md.update(headers != null ? headers.getBytes("UTF-8") : new byte[0]);
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return url + "|" + headers;
        }
    }

    private Map<String, String> doWork(String url, String headers) {
        return fqEncryptService.generateSignatureHeaders(url, headers);
    }

    public void reset() {
        synchronized (resetLock) {
            signatureCacheService.invalidateAll();

            if (this.unidbgProperties.isAsync()) {
                WorkerPool previousPool = this.pool;
                this.pool = null;
                if (previousPool != null) {
                    try {
                        previousPool.close();
                    } catch (Exception e) {
                        log.warn("关闭旧签名线程池失败，继续重建", e);
                    }
                }
                this.pool = createAsyncPool();
                log.info("FQ签名线程池重置完成，poolSize={}", this.poolSize);
                return;
            }

            if (fqEncryptService == null) {
                fqEncryptService = new FQEncryptService(unidbgProperties);
            } else {
                fqEncryptService.reset();
            }
            log.info("FQ签名服务重置完成（同步模式）");
        }
    }

    private WorkerPool createAsyncPool() {
        return WorkerPoolFactory.create(pool -> new FQEncryptServiceWorker(
            unidbgProperties.isDynarmic(),
            unidbgProperties.isVerbose(),
            pool
        ), poolSize);
    }

    @PreDestroy
    @Override
    public void destroy() {
        synchronized (resetLock) {
            WorkerPool previousPool = this.pool;
            this.pool = null;
            if (previousPool != null) {
                try {
                    previousPool.close();
                } catch (Exception e) {
                    log.warn("关闭签名线程池失败", e);
                }
            }

            if (fqEncryptService != null) {
                fqEncryptService.destroy();
            }
        }
    }

    public Map<String, Object> getCacheStats() {
        CacheStats stats = signatureCacheService.getStats();
        return Map.of(
            "cacheSize", signatureCacheService.size(),
            "hitCount", stats.hitCount(),
            "missCount", stats.missCount(),
            "hitRate", String.format("%.2f%%", stats.hitRate() * 100),
            "evictionCount", stats.evictionCount()
        );
    }
}
