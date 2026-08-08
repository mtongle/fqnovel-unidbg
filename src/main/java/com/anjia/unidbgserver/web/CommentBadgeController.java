package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.utils.BadgeImageRenderer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 评论数徽章图片端点
 * <p>
 * 章节正文中段评徽章以相对 URL 引用（D1/D2）：
 * {@code /api/fqnovel/comment-badge/{count},...} —— 浏览器/WebView 请求时可能携带
 * 点击元数据后缀（含 {@code ,{"click":...}}，会被百分号编码），因此路径段做
 * 「取前导数字、忽略其后内容」的容错解析。
 * <p>
 * PNG 字节按 count 做进程内缓存（上限约 128 条，超限清空重建）。
 */
@Slf4j
@RestController
@RequestMapping(path = "/api/fqnovel")
public class CommentBadgeController {

    /** 进程内徽章 PNG 缓存上限（条） */
    private static final int CACHE_MAX_SIZE = 128;

    private final ConcurrentHashMap<Integer, byte[]> badgeCache = new ConcurrentHashMap<>();

    @GetMapping("/comment-badge/{count}")
    public ResponseEntity<byte[]> getCommentBadge(@PathVariable String count) {
        int badgeCount = parseCount(count);
        byte[] png = badgeCache.get(badgeCount);
        if (png == null) {
            png = BadgeImageRenderer.renderPng(badgeCount);
            if (badgeCache.size() >= CACHE_MAX_SIZE) {
                badgeCache.clear();
            }
            badgeCache.put(badgeCount, png);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .cacheControl(CacheControl.maxAge(86400, TimeUnit.SECONDS).cachePublic())
                .body(png);
    }

    /**
     * 容错解析路径段：只取前导数字，忽略 {@code ,{...}} 之后的点击元数据后缀。
     * 无数字前缀或 count &le; 0 时返回 400（由 GlobalExceptionHandler 统一处理）。
     */
    private int parseCount(String segment) {
        if (segment == null || segment.isEmpty()) {
            throw new IllegalArgumentException("评论数不能为空");
        }
        int end = 0;
        while (end < segment.length() && Character.isDigit(segment.charAt(end))) {
            end++;
        }
        if (end == 0) {
            throw new IllegalArgumentException("评论数必须为非零数字前缀: " + segment);
        }
        int count;
        try {
            count = Integer.parseInt(segment.substring(0, end));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("评论数超出范围: " + segment);
        }
        if (count <= 0) {
            throw new IllegalArgumentException("评论数必须为正数");
        }
        return count;
    }
}