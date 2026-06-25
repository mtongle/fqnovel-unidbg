package com.anjia.unidbgserver.utils;

import lombok.extern.slf4j.Slf4j;

import java.util.regex.Pattern;

/**
 * 通用工具类
 * 集中管理静态正则表达式和字符串工具方法
 */
@Slf4j
public final class CommonUtils {

    // ========== 正则表达式常量 ==========

    /** 提取 <blk> 标签文本 */
    public static final Pattern BLK_PATTERN = Pattern.compile(
            "<blk[^>]*>([^<]*)</blk>", Pattern.CASE_INSENSITIVE);

    /** 提取 h1 标题 */
    public static final Pattern H1_TITLE_PATTERN = Pattern.compile(
            "<h1[^>]*>.*?<blk[^>]*>([^<]*)</blk>.*?</h1>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

    /** 移除 HTML 标签 */
    public static final Pattern HTML_TAG_PATTERN = Pattern.compile("<[^>]+>");

    /** 替换换行和制表符 */
    public static final Pattern WHITESPACE_PATTERN = Pattern.compile("[\\r\\n\\t]");

    /** 提取 Android 版本号 */
    public static final Pattern ANDROID_VERSION_PATTERN = Pattern.compile("Android\\s+([^;\\s]+)");

    private CommonUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ========== Hex 编解码 ==========

    private static final char[] HEX_ARRAY = "0123456789abcdef".toCharArray();

    /**
     * 字节数组转十六进制字符串（小写，性能优化版）
     * 替代 String.format("%02x", b) 循环，性能提升约 20x
     */
    public static String bytesToHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int v = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[v >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }

    /**
     * 十六进制字符串转字节数组
     */
    public static byte[] hexToBytes(String hexString) {
        if (hexString == null || hexString.isEmpty()) {
            return new byte[0];
        }
        int len = hexString.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hexString.charAt(i), 16) << 4)
                    + Character.digit(hexString.charAt(i + 1), 16));
        }
        return data;
    }

    // ========== 字符串工具 ==========

    /**
     * 判断字符串是否非空（去除空格后）
     */
    public static boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }

    /**
     * 返回第一个非空值
     */
    public static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    /**
     * 安全地裁剪字符串用于日志预览
     */
    public static String preview(String content, int maxLen) {
        if (content == null) {
            return "null";
        }
        String normalized = WHITESPACE_PATTERN.matcher(content).replaceAll(" ");
        return normalized.length() <= maxLen ? normalized : normalized.substring(0, maxLen) + "...";
    }

    /**
     * 安全地裁剪字符串（默认 64 字符）
     */
    public static String preview(String content) {
        return preview(content, 64);
    }

    /**
     * 简单的 HTML 转义（XML 实体）
     */
    public static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * JSON 字符串转义
     */
    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * URL 参数编码
     */
    public static String urlEncode(String s) {
        if (s == null) return "";
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

//    // ========== 图片 URL 标准化 ==========
//
//    /**
//     * 标准化图片 URL：
//     * - HEIC/HEIF 格式 → 代理路由 /api/img/proxy（服务端转换为 JPEG）
//     * - 非 HEIC → 返回 HTTPS URL（直连 CDN）
//     *
//     * 注意：不能直接改 .heic → .jpg，CDN 返回 403
//     *
//     * 支持识别格式：.heic, .heif, ~heic, ~heif（带查询参数也能识别）
//     */
//    public static String normalizeImageUrl(String url) {
//        if (url == null || url.isEmpty()) return url;
//
//        // 统一使用 HTTPS
//        String result = url.replace("http://", "https://");
//
//        // 提取路径部分（去掉查询参数）再检查后缀
//        String path = result;
//        int qIdx = path.indexOf('?');
//        if (qIdx > 0) path = path.substring(0, qIdx);
//
//        String lowerPath = path.toLowerCase();
//        boolean isHeic = lowerPath.endsWith(".heic") || lowerPath.endsWith(".heif")
//                      || lowerPath.endsWith("~heic") || lowerPath.endsWith("~heif");
//
//        if (isHeic) {
//            String proxiedUrl = "/api/img/proxy?url=" + urlEncode(result);
//            return proxiedUrl;
//        }
//
//        return result;
//    }

    // ========== 响应错误检测 ==========

    /**
     * 判断异常是否为空响应错误
     */
    public static boolean isEmptyResponseError(Exception e) {
        if (e == null) return false;
        String message = e.getMessage();
        return "EMPTY_RESPONSE".equals(message)
                || (message != null && message.contains("No content to map due to end-of-input"));
    }
}
