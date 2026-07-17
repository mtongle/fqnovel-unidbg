package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.FQNovelChapterInfo;
import com.anjia.unidbgserver.dto.FQNovelResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
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

    // ========== generateSvgDataUri tests ==========

    @Test
    void generateSvgDataUri_startsWithDataUriPrefix() throws Exception {
        String result = invokeGenerateSvgDataUri(5, "http://example.com");
        assertNotNull(result);
        assertTrue(result.startsWith("data:image/svg+xml;base64,"));
    }

    @Test
    void generateSvgDataUri_containsSvgTagsInDecodedPortion() throws Exception {
        String result = invokeGenerateSvgDataUri(5, "http://example.com");
        assertNotNull(result);

        String base64Part = result.substring("data:image/svg+xml;base64,".length());
        // Split on first comma to separate base64 from click metadata
        int commaIdx = base64Part.indexOf(',');
        assertTrue(commaIdx > 0, "Should contain comma separating base64 from click meta");

        String svgBase64 = base64Part.substring(0, commaIdx);
        String decoded = new String(Base64.getDecoder().decode(svgBase64), StandardCharsets.UTF_8);

        assertTrue(decoded.contains("<svg"), "Decoded SVG should contain <svg");
        assertTrue(decoded.contains("</svg>"), "Decoded SVG should contain </svg>");
    }

    @Test
    void generateSvgDataUri_containsCountInSvg() throws Exception {
        int expectedCount = 42;
        String result = invokeGenerateSvgDataUri(expectedCount, "http://example.com");
        assertNotNull(result);

        String base64Part = result.substring("data:image/svg+xml;base64,".length());
        int commaIdx = base64Part.indexOf(',');
        String svgBase64 = base64Part.substring(0, commaIdx);
        String decoded = new String(Base64.getDecoder().decode(svgBase64), StandardCharsets.UTF_8);

        assertTrue(decoded.contains(String.valueOf(expectedCount)),
                "Decoded SVG should contain the count value");
    }

    @Test
    void generateSvgDataUri_endsWithClickMeta() throws Exception {
        String result = invokeGenerateSvgDataUri(5, "http://example.com");
        assertNotNull(result);

        assertTrue(result.contains("{\"click\":\"showCmt("),
                "Result should contain inline try-catch with java.showBrowser");
        assertTrue(result.contains("\",\"style\":\"text\"}"),
                "Result should contain style:text in metadata");
    }

    @Test
    void generateSvgDataUri_widthVariesByCount() throws Exception {
        String result3 = invokeGenerateSvgDataUri(3, "http://example.com");
        String base64Part3 = result3.substring("data:image/svg+xml;base64,".length());
        int commaIdx3 = base64Part3.indexOf(',');
        String svgBase643 = base64Part3.substring(0, commaIdx3);
        String decoded3 = new String(Base64.getDecoder().decode(svgBase643), StandardCharsets.UTF_8);
        assertTrue(decoded3.contains("width=\"28\""), "count=3 should have width=28");

        String result150 = invokeGenerateSvgDataUri(150, "http://example.com");
        String base64Part150 = result150.substring("data:image/svg+xml;base64,".length());
        int commaIdx150 = base64Part150.indexOf(',');
        String svgBase64150 = base64Part150.substring(0, commaIdx150);
        String decoded150 = new String(Base64.getDecoder().decode(svgBase64150), StandardCharsets.UTF_8);
        assertTrue(decoded150.contains("width=\"39\""), "count=150 should have width=39");

        String result1500 = invokeGenerateSvgDataUri(1500, "http://example.com");
        String base64Part1500 = result1500.substring("data:image/svg+xml;base64,".length());
        int commaIdx1500 = base64Part1500.indexOf(',');
        String svgBase641500 = base64Part1500.substring(0, commaIdx1500);
        String decoded1500 = new String(Base64.getDecoder().decode(svgBase641500), StandardCharsets.UTF_8);
        assertTrue(decoded1500.contains("width=\"48\""), "count=1500 should have width=48");
    }

    @Test
    void generateSvgDataUri_countZeroReturnsNull() throws Exception {
        String result = invokeGenerateSvgDataUri(0, "http://example.com");
        assertNull(result, "count=0 should return null");
    }

    @Test
    void generateSvgDataUri_countNegativeReturnsNull() throws Exception {
        String result = invokeGenerateSvgDataUri(-1, "http://example.com");
        assertNull(result, "count<0 should return null");
    }

    // ========== injectCommentIcons tests ==========

    @Test
    void injectCommentIcons_hasComments_injectsImgTag() throws Exception {
        String content = "第一段\n第二段\n第三段";
        Map<Integer, Integer> commentCounts = new HashMap<>();
        commentCounts.put(0, 5);

        String result = invokeInjectCommentIcons(content, commentCounts, "book1", "chapter1");
        assertTrue(result.contains("<img src='data:image"),
                "Should contain img tag with data URI when comments exist");
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
        commentCounts.put(0, 5); // first content paragraph has 5 comments
        commentCounts.put(1, 3); // second content paragraph has 3 comments

        String result = invokeInjectCommentIcons(content, commentCounts, "b1", "c1", "第一章 开始");
        assertTrue(result.startsWith("<p>第一章 开始</p>"),
                "Title line should be rendered without icon");
        assertTrue(result.contains("paraIndex=0") && result.contains("paraIndex=1"),
                "Content paragraphs should use paraIndex=0 and paraIndex=1");
        // Title is before first img; ensure no img between title and first content
        int titleEnd = result.indexOf("</p>") + 4;
        int firstImg = result.indexOf("<img");
        assertTrue(firstImg > titleEnd,
                "First img should appear after title paragraph, not on it");
    }

    private String invokeGenerateSvgDataUri(int count, String clickUrl) throws Exception {
        Method method = CommentEnrichmentService.class.getDeclaredMethod(
                "generateSvgDataUri", int.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, count, clickUrl);
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
