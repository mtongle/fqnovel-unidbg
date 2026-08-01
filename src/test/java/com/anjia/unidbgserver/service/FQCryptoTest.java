package com.anjia.unidbgserver.service;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FQCrypto 加解密正确性单元测试
 * 覆盖：AES-CBC 加解密、registerkey 加解密链路、gzip 解压、hex 校验
 */
class FQCryptoTest {

    /** 16 字节十六进制密钥（32 hex 字符） */
    private static final String HEX_KEY = "00112233445566778899aabbccddeeff";

    @Test
    void encryptDecrypt_roundTrip() throws Exception {
        FQCrypto crypto = new FQCrypto(HEX_KEY);
        byte[] plain = "Hello FQNovel 测试".getBytes(StandardCharsets.UTF_8);
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);

        byte[] encrypted = crypto.encrypt(plain, iv);
        assertNotEquals(plain.length, encrypted.length, "PKCS5 填充后长度应大于原文");

        // 拼接 IV + 密文 → base64 → decrypt 应还原
        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        String encoded = Base64.getEncoder().encodeToString(combined);

        byte[] decrypted = crypto.decrypt(encoded);
        assertArrayEquals(plain, decrypted, "解密结果应与原文一致");
    }

    @Test
    void newRegisterKeyContent_encryptsServerDeviceId() throws Exception {
        FQCrypto crypto = new FQCrypto(HEX_KEY);
        String serverDeviceId = "1234567890123456789";

        String content = crypto.newRegisterKeyContent(serverDeviceId, "0");
        assertNotNull(content);
        assertFalse(content.isEmpty());

        // 解密后前 8 字节应为 server_device_id（小端）
        byte[] raw = Base64.getDecoder().decode(content);
        assertTrue(raw.length >= 16 + 16, "IV(16) + 密文(至少16)");
        byte[] decrypted = crypto.decrypt(content);
        assertEquals(16, decrypted.length, "两个 long 共 16 字节");

        java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(decrypted).order(java.nio.ByteOrder.LITTLE_ENDIAN);
        assertEquals(Long.parseLong(serverDeviceId), buf.getLong(), "前 8 字节为 server_device_id");
        assertEquals(0L, buf.getLong(), "后 8 字节为 strVal=0");
    }

    @Test
    void decryptRegisterKey_returnsHexUpper() throws Exception {
        // 构造一个合法的 registerkey 响应：用 REG_KEY 加密 "16字节真实密钥" + 填充
        FQCrypto crypto = new FQCrypto(FQCrypto.REG_KEY);
        byte[] realKey = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.ISO_8859_1);
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        byte[] encrypted = crypto.encrypt(realKey, iv);

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        String registerKey = Base64.getEncoder().encodeToString(combined);

        String keyHex = FQCrypto.getRealKey(registerKey);
        assertEquals("30313233343536373839616263646566", keyHex,
                "getRealKey 应返回前 16 字节的大写 hex");
    }

    @Test
    void decryptAndDecompressContent_plainContent() throws Exception {
        FQCrypto crypto = new FQCrypto(HEX_KEY);
        String plain = "普通未压缩文本";
        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        byte[] encrypted = crypto.encrypt(plain.getBytes(StandardCharsets.UTF_8), iv);

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        String encoded = Base64.getEncoder().encodeToString(combined);

        String result = FQCrypto.decryptAndDecompressContent(encoded, HEX_KEY);
        assertEquals(plain, result, "非 gzip 内容直接返回 UTF-8 字符串");
    }

    @Test
    void decryptAndDecompressContent_gzipContent() throws Exception {
        FQCrypto crypto = new FQCrypto(HEX_KEY);
        String plain = "这是一段会被 gzip 压缩的章节内容，包含中文与标点符号。";
        byte[] compressed = gzip(plain.getBytes(StandardCharsets.UTF_8));

        byte[] iv = new byte[16];
        new java.security.SecureRandom().nextBytes(iv);
        byte[] encrypted = crypto.encrypt(compressed, iv);

        byte[] combined = new byte[iv.length + encrypted.length];
        System.arraycopy(iv, 0, combined, 0, iv.length);
        System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);
        String encoded = Base64.getEncoder().encodeToString(combined);

        String result = FQCrypto.decryptAndDecompressContent(encoded, HEX_KEY);
        assertEquals(plain, result, "gzip 内容应解压后返回");
    }

    @Test
    void hexStringToByteArray_invalidInput() {
        assertThrows(IllegalArgumentException.class, () -> FQCrypto.hexStringToByteArray("abc"), "奇数长度应抛异常");
        assertThrows(IllegalArgumentException.class, () -> FQCrypto.hexStringToByteArray("zz"), "非法字符应抛异常");
        assertThrows(IllegalArgumentException.class, () -> FQCrypto.hexStringToByteArray(null), "null 应抛异常");
        assertArrayEquals(new byte[]{0x01, 0x2f}, FQCrypto.hexStringToByteArray("012f"), "合法 hex 应正确解析");
    }

    @Test
    void constructor_rejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new FQCrypto("short"), "过短 key 应抛异常");
        assertThrows(IllegalArgumentException.class, () -> new FQCrypto("gg" + "11".repeat(15)), "非法 hex 应抛异常");
    }

    private byte[] gzip(byte[] data) throws java.io.IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        try (java.util.zip.GZIPOutputStream gzip = new java.util.zip.GZIPOutputStream(bos)) {
            gzip.write(data);
        }
        return bos.toByteArray();
    }
}
