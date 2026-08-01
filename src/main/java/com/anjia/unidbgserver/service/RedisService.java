package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.FQNovelBookInfo;
import com.anjia.unidbgserver.dto.FQNovelChapterInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Redis服务类
 */
@Slf4j
@Service
public class RedisService {

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String CHAPTER_KEY_PREFIX = "novel:chapter:";
    private static final String BOOK_CHAPTERS_KEY_PREFIX = "novel:book:chapters:";
    private static final int DEFAULT_EXPIRE_DAYS = 7; // 默认7天过期

    /**
     * 保存章节信息到Redis
     */
    public void saveChapter(String bookId, String chapterId, FQNovelChapterInfo chapterInfo) {
        try {
            String key = CHAPTER_KEY_PREFIX + bookId + ":" + chapterId;
            String value = objectMapper.writeValueAsString(chapterInfo);
            
            redisTemplate.opsForValue().set(key, value, DEFAULT_EXPIRE_DAYS, TimeUnit.DAYS);
            
            // 将章节ID添加到书籍的章节列表中
            String bookChaptersKey = BOOK_CHAPTERS_KEY_PREFIX + bookId;
            redisTemplate.opsForSet().add(bookChaptersKey, chapterId);
            redisTemplate.expire(bookChaptersKey, DEFAULT_EXPIRE_DAYS, TimeUnit.DAYS);
            
            log.debug("章节保存到Redis成功 - bookId: {}, chapterId: {}", bookId, chapterId);
            
        } catch (JsonProcessingException e) {
            log.error("保存章节到Redis失败 - bookId: {}, chapterId: {}", bookId, chapterId, e);
        }
    }

    /**
     * 从Redis获取章节信息
     */
    public FQNovelChapterInfo getChapter(String bookId, String chapterId) {
        try {
            String key = CHAPTER_KEY_PREFIX + bookId + ":" + chapterId;
            String value = redisTemplate.opsForValue().get(key);
            
            if (value != null) {
                return objectMapper.readValue(value, FQNovelChapterInfo.class);
            }
            
        } catch (JsonProcessingException e) {
            log.error("从Redis获取章节失败 - bookId: {}, chapterId: {}", bookId, chapterId, e);
        }
        
        return null;
    }

    /**
     * 检查章节是否存在于Redis中
     */
    public boolean hasChapter(String bookId, String chapterId) {
        String key = CHAPTER_KEY_PREFIX + bookId + ":" + chapterId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * 获取书籍的所有章节ID列表
     */
    public java.util.Set<String> getBookChapterIds(String bookId) {
        String bookChaptersKey = BOOK_CHAPTERS_KEY_PREFIX + bookId;
        return redisTemplate.opsForSet().members(bookChaptersKey);
    }

    /**
     * 删除章节
     */
    public void deleteChapter(String bookId, String chapterId) {
        String key = CHAPTER_KEY_PREFIX + bookId + ":" + chapterId;
        redisTemplate.delete(key);
        
        // 从书籍章节列表中移除
        String bookChaptersKey = BOOK_CHAPTERS_KEY_PREFIX + bookId;
        redisTemplate.opsForSet().remove(bookChaptersKey, chapterId);
        
        log.debug("章节从Redis删除成功 - bookId: {}, chapterId: {}", bookId, chapterId);
    }

    /**
     * 删除书籍的所有章节
     */
    public void deleteBookChapters(String bookId) {
        // 获取所有章节ID
        java.util.Set<String> chapterIds = getBookChapterIds(bookId);
        
        if (chapterIds != null && !chapterIds.isEmpty()) {
            // 删除所有章节
            for (String chapterId : chapterIds) {
                String key = CHAPTER_KEY_PREFIX + bookId + ":" + chapterId;
                redisTemplate.delete(key);
            }
        }
        
        // 删除书籍章节列表
        String bookChaptersKey = BOOK_CHAPTERS_KEY_PREFIX + bookId;
        redisTemplate.delete(bookChaptersKey);
        
        log.info("书籍所有章节从Redis删除成功 - bookId: {}", bookId);
    }

    /**
     * 获取书籍已下载章节数量
     */
    public long getBookDownloadedChapterCount(String bookId) {
        String bookChaptersKey = BOOK_CHAPTERS_KEY_PREFIX + bookId;
        Long count = redisTemplate.opsForSet().size(bookChaptersKey);
        return count != null ? count : 0;
    }

    /**
     * 设置键的过期时间
     */
    public void setExpire(String key, long timeout, TimeUnit unit) {
        redisTemplate.expire(key, timeout, unit);
    }

    /**
     * 检查键是否存在
     */
    public boolean hasKey(String key) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /** 作品信息 key（TTL 30 天） */
    private static final String BOOK_INFO_KEY_PREFIX = "book:";
    private static final String BOOK_INFO_KEY_SUFFIX = ":info";
    /** 章节列表 key（旧风格，TTL 30 天） */
    private static final String BOOK_CHAPTER_LIST_KEY_PREFIX = "book:";
    private static final String BOOK_CHAPTER_LIST_KEY_SUFFIX = ":chapters";
    private static final Duration BOOK_INFO_EXPIRE = Duration.ofDays(30);
    private static final Duration CHAPTER_LIST_EXPIRE = Duration.ofDays(30);

    /**
     * 保存作品信息到Redis
     */
    public void saveBookInfo(String bookId, FQNovelBookInfo bookInfo) {
        try {
            String key = BOOK_INFO_KEY_PREFIX + bookId + BOOK_INFO_KEY_SUFFIX;
            String json = objectMapper.writeValueAsString(bookInfo);
            redisTemplate.opsForValue().set(key, json, BOOK_INFO_EXPIRE);
            log.debug("作品信息保存成功 - bookId: {}", bookId);
        } catch (Exception e) {
            log.error("保存作品信息失败 - bookId: {}", bookId, e);
        }
    }

    /**
     * 从Redis获取作品信息
     */
    public FQNovelBookInfo getBookInfo(String bookId) {
        try {
            String key = BOOK_INFO_KEY_PREFIX + bookId + BOOK_INFO_KEY_SUFFIX;
            String json = (String) redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, FQNovelBookInfo.class);
            }
            return null;
        } catch (Exception e) {
            log.error("获取作品信息失败 - bookId: {}", bookId, e);
            return null;
        }
    }

    /**
     * 保存章节列表到Redis
     */
    public void saveChapterList(String bookId, List<String> chapterIds) {
        try {
            String key = BOOK_CHAPTER_LIST_KEY_PREFIX + bookId + BOOK_CHAPTER_LIST_KEY_SUFFIX;
            String json = objectMapper.writeValueAsString(chapterIds);
            redisTemplate.opsForValue().set(key, json, CHAPTER_LIST_EXPIRE);
            log.debug("章节列表保存成功 - bookId: {}, 章节数量: {}", bookId, chapterIds.size());
        } catch (Exception e) {
            log.error("保存章节列表失败 - bookId: {}", bookId, e);
        }
    }

    /**
     * 从Redis获取章节列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getChapterList(String bookId) {
        try {
            String key = BOOK_CHAPTER_LIST_KEY_PREFIX + bookId + BOOK_CHAPTER_LIST_KEY_SUFFIX;
            String json = (String) redisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, List.class);
            }
            return null;
        } catch (Exception e) {
            log.error("获取章节列表失败 - bookId: {}", bookId, e);
            return null;
        }
    }

    /**
     * 检查作品信息是否存在
     */
    public boolean hasBookInfo(String bookId) {
        try {
            String key = BOOK_INFO_KEY_PREFIX + bookId + BOOK_INFO_KEY_SUFFIX;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查作品信息失败 - bookId: {}", bookId, e);
            return false;
        }
    }

    /**
     * 检查章节列表是否存在
     */
    public boolean hasChapterList(String bookId) {
        try {
            String key = BOOK_CHAPTER_LIST_KEY_PREFIX + bookId + BOOK_CHAPTER_LIST_KEY_SUFFIX;
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("检查章节列表失败 - bookId: {}", bookId, e);
            return false;
        }
    }

    /**
     * 删除作品的所有数据（包括作品信息、章节列表、所有章节内容）
     *
     * 注意：项目中存在两套 key 命名风格（历史遗留）：
     * - 作品信息: book:{bookId}:info
     * - 章节列表: book:{bookId}:chapters（旧） / novel:book:chapters:{bookId}（新，集合）
     * - 章节内容: novel:chapter:{bookId}:{chapterId}
     * deleteBook 需要同时清理两套，否则会留下孤儿数据。
     */
    public void deleteBook(String bookId) {
        try {
            Set<String> keys = new java.util.HashSet<>();

            // 旧前缀：book:{bookId}:*（作品信息 + 旧章节列表）
            Set<String> legacyKeys = scanKeys("book:" + bookId + ":*");
            if (legacyKeys != null) {
                keys.addAll(legacyKeys);
            }

            // 新前缀：章节列表集合
            keys.add(BOOK_CHAPTERS_KEY_PREFIX + bookId);

            // 新前缀：所有章节内容 novel:chapter:{bookId}:*
            Set<String> chapterKeys = scanKeys(CHAPTER_KEY_PREFIX + bookId + ":*");
            if (chapterKeys != null) {
                keys.addAll(chapterKeys);
            }

            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("删除作品所有数据成功 - bookId: {}, 删除数量: {}", bookId, keys.size());
            }
        } catch (Exception e) {
            log.error("删除作品所有数据失败 - bookId: {}", bookId, e);
        }
    }

    /**
     * 获取所有书籍相关的Redis key
     */
    public Set<String> getAllBookKeys() {
        Set<String> allKeys = new java.util.HashSet<>();
        
        Set<String> bookInfoKeys = scanKeys("book:*:info");
        if (bookInfoKeys != null) {
            allKeys.addAll(bookInfoKeys);
        }
        
        Set<String> bookChaptersKeys = scanKeys("book:*:chapters");
        if (bookChaptersKeys != null) {
            allKeys.addAll(bookChaptersKeys);
        }
        
        Set<String> novelChaptersKeys = scanKeys("novel:book:chapters:*");
        if (novelChaptersKeys != null) {
            allKeys.addAll(novelChaptersKeys);
        }
        
        log.debug("获取到 {} 个书籍相关的Redis key", allKeys.size());
        return allKeys;
    }

    private Set<String> scanKeysInternal(String pattern) {
        Set<String> keys = new java.util.HashSet<>();
        try {
            redisTemplate.execute((RedisConnection connection) -> {
                try (Cursor<byte[]> cursor = connection.scan(ScanOptions.scanOptions()
                        .match(pattern)
                        .count(100)
                        .build())) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                } catch (Exception e) {
                    log.warn("SCAN执行失败 - pattern: {}", pattern, e);
                }
                return null;
            });
        } catch (Exception e) {
            log.error("SCAN执行异常 - pattern: {}", pattern, e);
        }
        return keys;
    }

    /**
     * 使用 SCAN 查询匹配的键（供 CacheController 等外部调用）
     */
    public Set<String> scanKeys(String pattern) {
        return scanKeysInternal(pattern);
    }
}
