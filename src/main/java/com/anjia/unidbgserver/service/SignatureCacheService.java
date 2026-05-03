package com.anjia.unidbgserver.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class SignatureCacheService {

    private Cache<String, Map<String, String>> signatureCache;

    @PostConstruct
    public void init() {
        this.signatureCache = Caffeine.newBuilder()
            .maximumSize(5000)
            .expireAfterWrite(30, TimeUnit.MINUTES)
            .recordStats()
            .build();

        log.info("签名结果缓存初始化完成，最大容量: 5000, 过期时间: 30分钟");
    }

    public Map<String, String> getSignature(String cacheKey) {
        return signatureCache.getIfPresent(cacheKey);
    }

    public void putSignature(String cacheKey, Map<String, String> headers) {
        if (headers == null || headers.containsKey("error")) {
            return;
        }
        signatureCache.put(cacheKey, headers);
    }

    public void invalidateAll() {
        signatureCache.invalidateAll();
        log.info("签名缓存已清空");
    }

    public long size() {
        return signatureCache.estimatedSize();
    }

    public com.github.benmanes.caffeine.cache.stats.CacheStats getStats() {
        return signatureCache.stats();
    }
}
