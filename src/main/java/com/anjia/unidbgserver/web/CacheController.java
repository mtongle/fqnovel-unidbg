package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.dto.FQNovelBookInfo;
import com.anjia.unidbgserver.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@RestController
@RequestMapping("/api/cache")
@RequiredArgsConstructor
public class CacheController {

    private final RedisService redisService;

    private final StringRedisTemplate stringRedisTemplate;

    @GetMapping("/book/{bookId}/info")
    public ResponseEntity<?> getBookInfo(@PathVariable String bookId) {
        FQNovelBookInfo info = redisService.getBookInfo(bookId);
        if (info == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(info);
    }

    @GetMapping("/book/{bookId}/chapters")
    public ResponseEntity<?> getBookChapters(@PathVariable String bookId) {
        List<String> list = redisService.getChapterList(bookId);
        if (list == null) {
            return ResponseEntity.notFound().build();
        }
        Map<String, Object> resp = new HashMap<>();
        resp.put("bookId", bookId);
        resp.put("count", list.size());
        resp.put("chapterIds", list);
        return ResponseEntity.ok(resp);
    }

    /**
     * 查询缓存键（使用 SCAN 而非 KEYS，避免大数据量下阻塞 Redis 实例）
     */
    @GetMapping("/keys")
    public ResponseEntity<?> listKeys(@RequestParam String pattern) {
        Set<String> keys = redisService.scanKeys(pattern);
        Map<String, Object> resp = new HashMap<>();
        resp.put("pattern", pattern);
        resp.put("count", keys == null ? 0 : keys.size());
        resp.put("keys", keys == null ? Collections.emptyList() : keys);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/value")
    public ResponseEntity<?> getValue(@RequestParam String key) {
        String val = stringRedisTemplate.opsForValue().get(key);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", key);
        resp.put("value", val);
        return ResponseEntity.ok(resp);
    }

    @GetMapping("/delete")
    public ResponseEntity<?> deleteKeyGet(@RequestParam String key) {
        return deleteKey(key);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<?> deleteKey(@RequestParam String key) {
        Boolean deleted = stringRedisTemplate.delete(key);
        Map<String, Object> resp = new HashMap<>();
        resp.put("key", key);
        resp.put("deleted", deleted);
        return ResponseEntity.ok(resp);
    }
}
