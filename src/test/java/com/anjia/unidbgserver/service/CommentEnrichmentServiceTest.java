package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.FQNovelChapterInfo;
import com.anjia.unidbgserver.dto.FQNovelResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class CommentEnrichmentServiceTest {

    @Mock
    private FQCommentService fqCommentService;

    @InjectMocks
    private CommentEnrichmentService service;

    // ========== generateBadgeSrc tests ==========

    @Test
    void generateBadgeSrc_startsWithBadgePath() throws Exception {
        String result = invokeGenerateBadgeSrc(5, "http://example.com");
        assertNotNull(result);
        assertTrue(result.startsWith("/api/fqnovel/comment-badge/"));
    }

    @Test
    void generateBadgeSrc_containsClickMeta() throws Exception {
        String result = invokeGenerateBadgeSrc(5, "http://example.com");
        assertNotNull(result);
        assertTrue(result.contains("{\"click\":\"showCmt("),
                "Result should contain click metadata with showCmt");
        assertTrue(result.contains("\",\"style\":\"text\"}"),
                "Result should contain style:text in metadata");
    }

    @Test
    void generateBadgeSrc_noRawSingleQuote() throws Exception {
        String result = invokeGenerateBadgeSrc(5, "http://example.com");
        assertNotNull(result);
        assertFalse(result.contains("'"),
                "Attribute value must not contain any raw single quote (src is single-quoted)");
    }

    @Test
    void generateBadgeSrc_containsEscapedClickUrl() throws Exception {
        String result = invokeGenerateBadgeSrc(
                5, "/api/ssr/comment-page?bookId=b1&chapterId=c1&paraIndex=0");
        assertNotNull(result);
        assertTrue(result.contains("showCmt(\\\"/api/ssr/comment-page?bookId=b1&chapterId=c1&paraIndex=0\\\""),
                "Click URL should be wrapped in escaped double quotes (JSON-escaped JS)");
        assertTrue(result.contains(",\\\"番茄\\\",true"),
                "Click JS should carry the 番茄 source flag with escaped quotes");
    }

    @Test
    void generateBadgeSrc_countZeroReturnsNull() throws Exception {
        String result = invokeGenerateBadgeSrc(0, "http://example.com");
        assertNull(result, "count=0 should return null");
    }

    @Test
    void generateBadgeSrc_countNegativeReturnsNull() throws Exception {
        String result = invokeGenerateBadgeSrc(-1, "http://example.com");
        assertNull(result, "count<0 should return null");
    }

    // ========== injectCommentIcons tests ==========

    @Test
    void injectCommentIcons_hasComments_injectsImgTag() throws Exception {
        String content = "第一段\n第二段\n第三段";
        Map<Integer, Integer> commentCounts = new HashMap<>();
        commentCounts.put(0, 5);

        String result = invokeInjectCommentIcons(content, commentCounts, "book1", "chapter1");
        assertTrue(result.contains("<img src='/api/fqnovel/comment-badge/"),
                "Should contain img tag with badge HTTP URL when comments exist");
        assertTrue(result.contains("bookId=book1"), "Should contain bookId in URL");
        assertTrue(result.contains("chapterId=chapter1"), "Should contain chapterId in URL");
        assertTrue(result.contains("paraIndex=0"), "Should contain paraIndex=0");
    }

    @Test
    void injectCommentIcons_noComments_noImgTag() throws Exception {
        String content = "第一段\n第二段\n第三段";
        Map<Integer, Integer> commentCounts = new HashMap<>();

        String result = invokeInjectCommentIcons(content, commentCounts, "book1", "chapter1");
        assertFalse(result.contains("<img"), "Should not contain img tags when no comments");
        assertEquals("<p>第一段</p>\n<p>第二段</p>\n<p>第三段</p>", result);
    }

    @Test
    void injectCommentIcons_htmlEscaped() throws Exception {
        String content = "文本 & 符号 <标签>";
        Map<Integer, Integer> commentCounts = new HashMap<>();
        commentCounts.put(0, 3);

        String result = invokeInjectCommentIcons(content, commentCounts, "b1", "c1");
        assertTrue(result.contains("&amp;"), "& should be escaped");
        assertTrue(result.contains("&lt;"), "< should be escaped");
        assertTrue(result.contains("&gt;"), "> should be escaped");
    }

    @Test
    void injectCommentIcons_emptyContent_returnsEmpty() throws Exception {
        String result = invokeInjectCommentIcons("", new HashMap<>(), "b1", "c1");
        assertEquals("", result, "Empty content should return empty string");
    }

    @Test
    void injectCommentIcons_trailingNewline_handledGracefully() throws Exception {
        String content = "段落1\n";
        Map<Integer, Integer> commentCounts = new HashMap<>();
        commentCounts.put(0, 5);

        String result = invokeInjectCommentIcons(content, commentCounts, "b1", "c1");
        assertTrue(result.contains("段落1"), "Should include paragraph text");
        assertTrue(result.contains("<img"), "Should include img tag");
    }

    @Test
    void injectCommentIcons_usesRelativePath() throws Exception {
        String content = "第一段\n第二段";
        Map<Integer, Integer> commentCounts = new HashMap<>();
        commentCounts.put(0, 3);

        String result = invokeInjectCommentIcons(content, commentCounts, "b1", "c1");
        assertTrue(result.contains("/api/ssr/comment-page?bookId=b1&chapterId=c1&paraIndex=0"),
                "Should contain relative comment page path");
    }

    @Test
    void injectCommentIcons_titleLine_noIconOnTitle() throws Exception {
        String content = "第一章 开始\n\n正文第一段\n\n正文第二段";
        Map<Integer, Integer> commentCounts = new HashMap<>();
        commentCounts.put(1, 5); // first content paragraph (index 1) has 5 comments
        commentCounts.put(3, 3); // second content paragraph (index 3) has 3 comments

        String result = invokeInjectCommentIcons(content, commentCounts, "b1", "c1", "第一章 开始");
        assertTrue(result.startsWith("<p>第一章 开始</p>"),
                "Title line should be rendered without icon");
        // 段落索引与 API para_index 对齐（实况验证：标题行不占索引）：
        // 标题行(不递增)、空行(1)、正文第一段(1)、空行(2)、正文第二段(3)
        assertTrue(result.contains("paraIndex=1") && result.contains("paraIndex=3"),
                "Content paragraphs should use paraIndex=1 and paraIndex=3 (title does not occupy index 0)");
        // Title is before first img; ensure no img between title and first content
        int titleEnd = result.indexOf("</p>") + 4;
        int firstImg = result.indexOf("<img");
        assertTrue(firstImg > titleEnd,
                "First img should appear after title paragraph, not on it");
    }

    @Test
    void injectCommentIcons_titleLine_notCountedInParaIndex() throws Exception {
        // 实况验证：API 的 para_index 0 对应首个正文段落（标题行不占索引）
        String content = "第一章 标题\n正文第一段\n正文第二段";
        Map<Integer, Integer> commentCounts = new HashMap<>();
        commentCounts.put(0, 7); // 首个正文段落 → para_index 0
        commentCounts.put(1, 3); // 第二个正文段落 → para_index 1

        String result = invokeInjectCommentIcons(content, commentCounts, "b1", "c1", "第一章 标题");
        assertTrue(result.contains("paraIndex=0") && result.contains("paraIndex=1"),
                "First content paragraph should map to para_index=0 (title line not counted)");
        assertFalse(result.contains("paraIndex=2"),
                "No icon should be shifted one line up onto the previous paragraph");
    }

    @Test
    void injectCommentIcons_blankLines_keepIndexAligned() throws Exception {
        // 空段落也占用 para_index（与 API 统计对齐），图标不因空行错位
        String content = "第一段\n\n第三段";
        Map<Integer, Integer> commentCounts = new HashMap<>();
        commentCounts.put(2, 5); // 第三段的评论在 para_index=2

        String result = invokeInjectCommentIcons(content, commentCounts, "b1", "c1");
        assertTrue(result.contains("paraIndex=2"),
                "Blank line should advance index so para 3 maps to index 2");
        assertFalse(result.contains("paraIndex=1"),
                "No icon should be placed at the blank line index");
    }

    private String invokeGenerateBadgeSrc(int count, String commentUrl) throws Exception {
        Method method = CommentEnrichmentService.class.getDeclaredMethod(
                "generateBadgeSrc", int.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, count, commentUrl);
    }

    private String invokeInjectCommentIcons(
            String content, Map<Integer, Integer> commentCounts,
            String bookId, String chapterId) throws Exception {
        Method method = CommentEnrichmentService.class.getDeclaredMethod(
                "injectCommentIcons", String.class, Map.class, String.class, String.class, String.class);
        method.setAccessible(true);
        // passing null for title to keep existing tests working (no title detection)
        return (String) method.invoke(service, content, commentCounts, bookId, chapterId, null);
    }

    private String invokeInjectCommentIcons(
            String content, Map<Integer, Integer> commentCounts,
            String bookId, String chapterId, String title) throws Exception {
        Method method = CommentEnrichmentService.class.getDeclaredMethod(
                "injectCommentIcons", String.class, Map.class, String.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, content, commentCounts, bookId, chapterId, title);
    }
}
