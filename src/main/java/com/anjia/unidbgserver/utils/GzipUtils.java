package com.anjia.unidbgserver.utils;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * GZIP 解压缩工具类
 * 统一项目中所有 GZIP 响应解压逻辑
 */
@Slf4j
public final class GzipUtils {

    /** GZIP 魔数高字节 */
    private static final int GZIP_MAGIC_HIGH = 0x1f;
    /** GZIP 魔数低字节 */
    private static final int GZIP_MAGIC_LOW = 0x8b;
    /** 解压缓冲区大小 */
    private static final int BUFFER_SIZE = 1024;

    private GzipUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * 检测数据是否为 GZIP 压缩格式
     *
     * @param data 待检测数据
     * @return true 如果数据以 GZIP 魔数开头
     */
    public static boolean isGzip(byte[] data) {
        return data != null && data.length >= 2
                && (data[0] & 0xff) == GZIP_MAGIC_HIGH
                && (data[1] & 0xff) == GZIP_MAGIC_LOW;
    }

    /**
     * 检测响应头的 Content-Encoding 是否包含 gzip
     *
     * @param contentEncoding Content-Encoding 头值列表
     * @return true 如果包含 gzip
     */
    public static boolean isGzipEncoding(java.util.List<String> contentEncoding) {
        if (contentEncoding == null) {
            return false;
        }
        return contentEncoding.stream().anyMatch(v -> v != null && v.toLowerCase().contains("gzip"));
    }

    /**
     * 解压缩 GZIP 数据
     *
     * @param gzipData GZIP 压缩数据
     * @return 解压后的字节数组
     */
    public static byte[] decompress(byte[] gzipData) throws Exception {
        if (gzipData == null || gzipData.length == 0) {
            return new byte[0];
        }

        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(gzipData));
             ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[BUFFER_SIZE];
            int length;
            while ((length = gzipInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, length);
            }
            return byteArrayOutputStream.toByteArray();
        }
    }

    /**
     * 解压缩并转字符串（UTF-8）
     *
     * @param gzipData GZIP 压缩数据
     * @return 解压后的 UTF-8 字符串
     */
    public static String decompressToString(byte[] gzipData) throws Exception {
        byte[] decompressed = decompress(gzipData);
        return new String(decompressed, StandardCharsets.UTF_8);
    }

    /**
     * 智能解压：自动检测 GZIP 格式并解压，非 GZIP 数据直接转字符串
     *
     * @param data HTTP 响应体
     * @return 解码后的字符串
     */
    public static String decodeBody(byte[] data) throws Exception {
        if (data == null || data.length == 0) {
            return "";
        }

        if (isGzip(data)) {
            return decompressToString(data);
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    /**
     * 智能解压（支持 Content-Encoding 头检测）
     *
     * @param data             HTTP 响应体
     * @param contentEncoding Content-Encoding 头值列表
     * @return 解码后的字符串
     */
    public static String decodeBody(byte[] data, java.util.List<String> contentEncoding) throws Exception {
        if (data == null || data.length == 0) {
            return "";
        }

        boolean gzipEncoded = isGzipEncoding(contentEncoding);
        if (!gzipEncoded && isGzip(data)) {
            gzipEncoded = true;
        }

        if (gzipEncoded) {
            return decompressToString(data);
        }
        return new String(data, StandardCharsets.UTF_8);
    }
}
