package com.anjia.unidbgserver.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class SsrCommentServiceTest {

    private final SsrCommentService service = new SsrCommentService();
    private final ObjectMapper mapper = new ObjectMapper();

    // ==================== getCommentNode ====================

    @Test
    void getCommentNode_returnsCommonFromComment() throws Exception {
        JsonNode item = mapper.readTree(
                "{\"comment\":{\"common\":{\"content\":{\"text\":\"hello\"}}}}");
        JsonNode result = invokeStatic("getCommentNode", item);
        assertNotNull(result);
        assertEquals("hello", result.at("/content/text").asText());
    }

    @Test
    void getCommentNode_noCommentWrapper_usesItemDirectly() throws Exception {
        JsonNode item = mapper.readTree(
                "{\"common\":{\"content\":{\"text\":\"direct\"}}}");
        JsonNode result = invokeStatic("getCommentNode", item);
        assertNotNull(result);
        assertEquals("direct", result.at("/content/text").asText());
    }

    @Test
    void getCommentNode_nullItem_returnsNull() throws Exception {
        assertNull(invokeStatic("getCommentNode", (Object) null));
    }

    // ==================== getStatNode ====================

    @Test
    void getStatNode_returnsStatFromComment() throws Exception {
        JsonNode item = mapper.readTree(
                "{\"comment\":{\"stat\":{\"digg_count\":42,\"reply_count\":7}}}");
        JsonNode result = invokeStatic("getStatNode", item);
        assertNotNull(result);
        assertEquals(42, result.get("digg_count").asInt());
        assertEquals(7, result.get("reply_count").asInt());
    }

    @Test
    void getStatNode_noStat_returnsNull() throws Exception {
        JsonNode item = mapper.readTree(
                "{\"comment\":{\"common\":{}}}");
        assertNull(invokeStatic("getStatNode", item));
    }

    @Test
    void getStatNode_nullItem_returnsNull() throws Exception {
        assertNull(invokeStatic("getStatNode", (Object) null));
    }

    // ==================== findFirstArray ====================

    @Test
    void findFirstArray_returnsFirstMatchingArray() throws Exception {
        JsonNode root = mapper.readTree(
                "{\"data\":{\"list\":[1,2,3],\"comments\":[4,5]}}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "findFirstArray", JsonNode.class, String[].class);
        m.setAccessible(true);
        JsonNode result = (JsonNode) m.invoke(null, root, new String[]{"/data/other", "/data/list", "/data/comments"});
        assertNotNull(result);
        assertTrue(result.isArray());
        assertEquals(3, result.size());
    }

    @Test
    void findFirstArray_noMatch_returnsNull() throws Exception {
        JsonNode root = mapper.readTree("{\"data\":{}}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "findFirstArray", JsonNode.class, String[].class);
        m.setAccessible(true);
        assertNull(m.invoke(null, root, new String[]{"/data/nonexistent"}));
    }

    // ==================== firstText ====================

    @Test
    void firstText_findsFirstMatch() throws Exception {
        JsonNode node = mapper.readTree(
                "{\"user_name\":\"测试\",\"nickname\":\"备用\"}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "firstText", JsonNode.class, String[].class);
        m.setAccessible(true);
        String result = (String) m.invoke(null, node, new String[]{"/user_name", "/nickname"});
        assertEquals("测试", result);
    }

    @Test
    void firstText_noMatch_returnsNull() throws Exception {
        JsonNode node = mapper.readTree("{\"other\":\"value\"}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "firstText", JsonNode.class, String[].class);
        m.setAccessible(true);
        assertNull(m.invoke(null, node, new String[]{"/user_name"}));
    }

    // ==================== firstInt ====================

    @Test
    void firstInt_findsFirstMatch() throws Exception {
        JsonNode node = mapper.readTree(
                "{\"digg_count\":42,\"like_count\":10}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "firstInt", JsonNode.class, String[].class);
        m.setAccessible(true);
        int result = (int) m.invoke(null, node, new String[]{"/digg_count", "/like_count"});
        assertEquals(42, result);
    }

    @Test
    void firstInt_noMatch_returnsZero() throws Exception {
        JsonNode node = mapper.readTree("{}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "firstInt", JsonNode.class, String[].class);
        m.setAccessible(true);
        int result = (int) m.invoke(null, node, new String[]{"/missing"});
        assertEquals(0, result);
    }

    // ==================== firstLong ====================

    @Test
    void firstLong_findsFirstMatch() throws Exception {
        JsonNode node = mapper.readTree(
                "{\"create_time\":1234567890,\"time\":9876543210}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "firstLong", JsonNode.class, String[].class);
        m.setAccessible(true);
        long result = (long) m.invoke(null, node, new String[]{"/create_time", "/time"});
        assertEquals(1234567890L, result);
    }

    @Test
    void firstLong_noMatch_returnsZero() throws Exception {
        JsonNode node = mapper.readTree("{}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "firstLong", JsonNode.class, String[].class);
        m.setAccessible(true);
        long result = (long) m.invoke(null, node, new String[]{"/missing"});
        assertEquals(0L, result);
    }

    // ==================== firstBool ====================

    @Test
    void firstBool_findsTrue() throws Exception {
        JsonNode node = mapper.readTree(
                "{\"is_author\":true,\"other\":false}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "firstBool", JsonNode.class, String[].class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(null, node, new String[]{"/is_author"});
        assertTrue(result);
    }

    @Test
    void firstBool_noMatch_returnsFalse() throws Exception {
        JsonNode node = mapper.readTree("{}");
        Method m = SsrCommentService.class.getDeclaredMethod(
                "firstBool", JsonNode.class, String[].class);
        m.setAccessible(true);
        boolean result = (boolean) m.invoke(null, node, new String[]{"/is_author"});
        assertFalse(result);
    }

    // ==================== escapeHtml ====================

    @Test
    void escapeHtml_xssPrevention() throws Exception {
        String result = invokeStatic("escapeHtml", "<script>alert('xss')</script>");
        assertEquals("&lt;script&gt;alert(&#39;xss&#39;)&lt;/script&gt;", result);
    }

    @Test
    void escapeHtml_ampersand() throws Exception {
        String result = invokeStatic("escapeHtml", "a & b & c");
        assertEquals("a &amp; b &amp; c", result);
    }

    @Test
    void escapeHtml_quotes() throws Exception {
        String result = invokeStatic("escapeHtml", "say \"hello\" world");
        assertEquals("say &quot;hello&quot; world", result);
    }

    @Test
    void escapeHtml_nullInput_returnsEmpty() throws Exception {
        assertEquals("", invokeStatic("escapeHtml", (Object) null));
    }

    @Test
    void escapeHtml_normalText_unchanged() throws Exception {
        String result = invokeStatic("escapeHtml", "正常的文本123!@#");
        assertEquals("正常的文本123!@#", result);
    }

    @Test
    void escapeHtml_allSpecialChars() throws Exception {
        String result = invokeStatic("escapeHtml", "<tag attr=\"val\">'test' & more</tag>");
        assertTrue(result.contains("&lt;"));
        assertTrue(result.contains("&gt;"));
        assertTrue(result.contains("&quot;"));
        assertTrue(result.contains("&#39;"));
        assertTrue(result.contains("&amp;"));
    }

    // ==================== formatTime ====================

    @Test
    void formatTime_returnsJustNow() throws Exception {
        long now = System.currentTimeMillis() / 1000 - 5;
        assertEquals("刚刚", invokeStatic("formatTime", now));
    }

    @Test
    void formatTime_returnsMinutesAgo() throws Exception {
        long ts = (System.currentTimeMillis() / 1000) - 5 * 60;
        assertEquals("5分钟前", invokeStatic("formatTime", ts));
    }

    @Test
    void formatTime_returnsHoursAgo() throws Exception {
        long ts = (System.currentTimeMillis() / 1000) - 3 * 3600;
        assertEquals("3小时前", invokeStatic("formatTime", ts));
    }

    @Test
    void formatTime_returnsDaysAgo() throws Exception {
        long ts = (System.currentTimeMillis() / 1000) - 7 * 86400;
        assertEquals("7天前", invokeStatic("formatTime", ts));
    }

    @Test
    void formatTime_oldTimestamp_returnsMonthsAgo() throws Exception {
        long ts = (System.currentTimeMillis() / 1000) - 3L * 30 * 86400;
        assertEquals("3个月前", invokeStatic("formatTime", ts));
    }

    @Test
    void formatTime_zero_returnsEmpty() throws Exception {
        assertEquals("", invokeStatic("formatTime", 0L));
    }

    @Test
    void formatTime_negative_returnsEmpty() throws Exception {
        assertEquals("", invokeStatic("formatTime", -1L));
    }

    @Test
    void formatTime_millisecondsInput_handlesLargeTs() throws Exception {
        // When ts > 1e12, service divides by 1000
        long ms = (System.currentTimeMillis() - 5000); // 5 seconds ago in ms
        assertEquals("刚刚", invokeStatic("formatTime", ms));
    }

    @Test
    void formatTime_exactBoundaries() throws Exception {
        long now = System.currentTimeMillis() / 1000;
        // exactly 60 seconds → should be "1分钟前"
        assertEquals("1分钟前", invokeStatic("formatTime", now - 60));
        // exactly 3600 seconds → should be "1小时前"
        assertEquals("1小时前", invokeStatic("formatTime", now - 3600));
        // exactly 86400 seconds → should be "1天前"
        assertEquals("1天前", invokeStatic("formatTime", now - 86400));
        // exactly 2592000 seconds → should be "1个月前"
        assertEquals("1个月前", invokeStatic("formatTime", now - 2592000));
    }

    // ==================== convertEmoji ====================

    @Test
    void convertEmoji_knownTokens() throws Exception {
        String result = invokeStatic("convertEmoji", "[微笑][哭][爱慕]");
        assertTrue(result.contains("\uD83D\uDE0A"), "Should contain smile emoji");
        assertTrue(result.contains("\uD83D\uDE2D"), "Should contain cry emoji");
        assertTrue(result.contains("\uD83D\uDE0D"), "Should contain heart eyes emoji");
    }

    @Test
    void convertEmoji_unknownTokensUntouched() throws Exception {
        String result = invokeStatic("convertEmoji", "[unknown_emoji]");
        assertEquals("[unknown_emoji]", result);
    }

    @Test
    void convertEmoji_mixedKnownAndUnknown() throws Exception {
        String result = invokeStatic("convertEmoji", "你好[微笑]world[unknown]end");
        assertTrue(result.contains("\uD83D\uDE0A"));
        assertTrue(result.contains("[unknown]"));
    }

    @Test
    void convertEmoji_nullInput_returnsEmpty() throws Exception {
        assertEquals("", invokeStatic("convertEmoji", (Object) null));
    }

    @Test
    void convertEmoji_emptyString_returnsEmpty() throws Exception {
        assertEquals("", invokeStatic("convertEmoji", ""));
    }

    @Test
    void convertEmoji_noEmoji_unchanged() throws Exception {
        String result = invokeStatic("convertEmoji", "普通的文本内容");
        assertEquals("普通的文本内容", result);
    }

    // ==================== getAvatarColor ====================

    @Test
    void getAvatarColor_consistentColor() throws Exception {
        String color1 = invokeStatic("getAvatarColor", "A");
        String color2 = invokeStatic("getAvatarColor", "A");
        assertEquals(color1, color2, "Same letter should always return same color");
    }

    @Test
    void getAvatarColor_returnsValidHexColor() throws Exception {
        String color = invokeStatic("getAvatarColor", "Z");
        assertNotNull(color);
        assertTrue(color.startsWith("#"), "Color should start with #");
        assertEquals(7, color.length(), "Hex color should be 7 chars (e.g. #e74c3c)");
    }

    @Test
    void getAvatarColor_differentLetters() throws Exception {
        String colorA = invokeStatic("getAvatarColor", "A");
        String colorB = invokeStatic("getAvatarColor", "B");
        // Different letters may have same or different colors (modulo), but both valid
        assertTrue(colorA.startsWith("#"));
        assertTrue(colorB.startsWith("#"));
    }

    // ==================== renderErrorHtml ====================

    @Test
    void renderErrorHtml_returnsFullPage() throws Exception {
        Method m = SsrCommentService.class.getDeclaredMethod("renderErrorHtml", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "自定义错误消息");

        assertTrue(result.contains("<!DOCTYPE html>"), "Should contain DOCTYPE");
        assertTrue(result.contains("<html"), "Should contain html tag");
        assertTrue(result.contains("</html>"), "Should contain closing html tag");
        assertTrue(result.contains("自定义错误消息"), "Should contain the error message");
        assertTrue(result.contains("error"), "Should contain error CSS class");
    }

    @Test
    void renderErrorHtml_escapesMessage() throws Exception {
        Method m = SsrCommentService.class.getDeclaredMethod("renderErrorHtml", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "<script>alert(1)</script>");

        assertTrue(result.contains("&lt;script&gt;alert(1)&lt;/script&gt;"),
                "HTML special chars in error message should be escaped");
    }

    @Test
    void renderErrorHtml_containsThemeToggle() throws Exception {
        Method m = SsrCommentService.class.getDeclaredMethod("renderErrorHtml", String.class);
        m.setAccessible(true);
        String result = (String) m.invoke(service, "error");

        assertTrue(result.contains("toggleTheme"), "Should contain theme toggle script");
        assertTrue(result.contains("✦"), "Should contain toggle theme icon");
    }

    // ==================== Helper Methods ====================

    /**
     * Invoke a static method on SsrCommentService, auto-resolving parameter types.
     * Supports: JsonNode, String, long, int, boolean, and null args.
     * For varargs String[] methods, pass the array explicitly.
     */
    @SuppressWarnings("unchecked")
    private <T> T invokeStatic(String methodName, Object... args) throws Exception {
        Method method = resolveMethod(methodName, args);
        method.setAccessible(true);
        try {
            return (T) method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception) throw (Exception) cause;
            throw new RuntimeException(cause);
        }
    }

    private Method resolveMethod(String name, Object... args) throws NoSuchMethodException {
        // 1. Try exact type match
        Class<?>[] exactTypes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) {
            exactTypes[i] = typeOf(args[i]);
        }
        try {
            return SsrCommentService.class.getDeclaredMethod(name, exactTypes);
        } catch (NoSuchMethodException ignored) {
            // 2. Try matching by iterating declared methods
        }

        // 2. Iterate all methods to find compatible match
        for (Method m : SsrCommentService.class.getDeclaredMethods()) {
            if (!m.getName().equals(name)) continue;
            Class<?>[] paramTypes = m.getParameterTypes();
            if (paramTypes.length != args.length) continue;
            if (paramsMatch(paramTypes, args)) {
                return m;
            }
        }
        throw new NoSuchMethodException(name + " with " + args.length + " params");
    }

    private boolean paramsMatch(Class<?>[] paramTypes, Object[] args) {
        for (int i = 0; i < paramTypes.length; i++) {
            if (args[i] == null) continue; // null matches any reference type
            Class<?> argType = args[i].getClass();
            if (paramTypes[i] == argType) continue;
            // Primitive wrappers
            if (paramTypes[i] == long.class && argType == Long.class) continue;
            if (paramTypes[i] == int.class && argType == Integer.class) continue;
            if (paramTypes[i] == boolean.class && argType == Boolean.class) continue;
            // Array compatibility
            if (paramTypes[i].isArray() && argType.isArray()) {
                if (paramTypes[i].getComponentType().isAssignableFrom(argType.getComponentType())) continue;
            }
            // Assignable
            if (paramTypes[i].isAssignableFrom(argType)) continue;
            return false;
        }
        return true;
    }

    private Class<?> typeOf(Object arg) {
        if (arg == null) return Object.class;
        if (arg instanceof JsonNode) return JsonNode.class;
        if (arg instanceof String) return String.class;
        if (arg instanceof Long) return long.class;
        if (arg instanceof Integer) return int.class;
        if (arg instanceof Boolean) return boolean.class;
        if (arg instanceof String[]) return String[].class;
        return arg.getClass();
    }
}
