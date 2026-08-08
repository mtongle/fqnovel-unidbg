package com.anjia.unidbgserver.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 脱敏占位符( **** )替换逻辑测试：保存时占位符行用磁盘旧值替换 / 磁盘无键则删除，
 * 其余行逐字保留（对应 config-editor spec「脱敏占位符永不落盘」「未知键逐字保留」）。
 */
class ConfigManagementServiceTest {

    @TempDir
    Path tempDir;

    /* ---------- 单元：substituteRedactedPlaceholders ---------- */

    @Test
    void placeholderLineReplacedWithDiskValueKeepingInlineComment() {
        String disk = "spring:\n  redis:\n    password: real-secret # 行内注释\n    host: 127.0.0.1\n";
        String incoming = "spring:\n  redis:\n    password: ****\n    host: 127.0.0.1\n";
        String out = ConfigManagementService.substituteRedactedPlaceholders(incoming, disk);
        assertEquals(disk, out);
    }

    @Test
    void placeholderLineDroppedWhenKeyAbsentOnDisk() {
        String disk = "server:\n  port: 8099\n";
        String incoming = "server:\n  port: 8099\nfq:\n  api:\n    cookie: ****\n";
        String out = ConfigManagementService.substituteRedactedPlaceholders(incoming, disk);
        // cookie 行被删除，父级映射行保留，其余行逐字不变
        assertEquals("server:\n  port: 8099\nfq:\n  api:\n", out);
    }

    @Test
    void unknownKeysCommentsAndOrderPreservedByteForByte() {
        String disk = "x: 1\n";
        String incoming = "# 顶部注释\n\nunknown-key: keep-me\nserver:\n  port: 8099\n# 尾部注释\n";
        String out = ConfigManagementService.substituteRedactedPlaceholders(incoming, disk);
        assertEquals(incoming, out);
    }

    @Test
    void quotedPlaceholdersHandled() {
        String disk = "fq:\n  api:\n    cookie: realCookie123\n";
        String incomingDouble = "fq:\n  api:\n    cookie: \"****\"\n";
        String incomingSingle = "fq:\n  api:\n    cookie: '****'\n";
        assertEquals(disk, ConfigManagementService.substituteRedactedPlaceholders(incomingDouble, disk));
        assertEquals(disk, ConfigManagementService.substituteRedactedPlaceholders(incomingSingle, disk));
    }

    @Test
    void emptyDiskDropsAllPlaceholderLines() {
        String incoming = "spring:\n  redis:\n    password: ****\nserver:\n  port: 8099\n";
        String out = ConfigManagementService.substituteRedactedPlaceholders(incoming, "");
        assertEquals("spring:\n  redis:\nserver:\n  port: 8099\n", out);
    }

    @Test
    void nonPlaceholderValuesNeverTouched() {
        String disk = "";
        String incoming = "key: value\nnum: 42\nflag: true\nquoted: \"hello\"\n";
        String out = ConfigManagementService.substituteRedactedPlaceholders(incoming, disk);
        assertEquals(incoming, out);
    }

    /* ---------- 集成：saveConfigFromYaml 写入临时文件 ---------- */

    @Test
    void saveConfigFromYamlPreservesOldPasswordWhenPlaceholderSubmitted() throws Exception {
        Path cfg = tempDir.resolve("application.yml");
        Files.write(cfg, "spring:\n  redis:\n    password: old-secret # keep\n".getBytes(StandardCharsets.UTF_8));
        ConfigManagementService service = new ConfigManagementService();
        ReflectionTestUtils.setField(service, "configLocation", cfg.toString());

        service.saveConfigFromYaml("spring:\n  redis:\n    password: ****\n");

        String saved = new String(Files.readAllBytes(cfg), StandardCharsets.UTF_8);
        assertTrue(saved.contains("password: old-secret # keep"));
        assertFalse(saved.contains("****"));
    }

    @Test
    void saveConfigFromYamlUpdatesPasswordWhenNewValueProvided() throws Exception {
        Path cfg = tempDir.resolve("application.yml");
        Files.write(cfg, "spring:\n  redis:\n    password: old-secret\n".getBytes(StandardCharsets.UTF_8));
        ConfigManagementService service = new ConfigManagementService();
        ReflectionTestUtils.setField(service, "configLocation", cfg.toString());

        service.saveConfigFromYaml("spring:\n  redis:\n    password: new-secret\n");

        String saved = new String(Files.readAllBytes(cfg), StandardCharsets.UTF_8);
        assertTrue(saved.contains("password: new-secret"));
        assertFalse(saved.contains("old-secret"));
    }

    @Test
    void saveConfigFromYamlDropsPlaceholderForKeyNotOnDisk() throws Exception {
        Path cfg = tempDir.resolve("application.yml");
        Files.write(cfg, "server:\n  port: 8099\n".getBytes(StandardCharsets.UTF_8));
        ConfigManagementService service = new ConfigManagementService();
        ReflectionTestUtils.setField(service, "configLocation", cfg.toString());

        service.saveConfigFromYaml("server:\n  port: 8099\nfq:\n  api:\n    cookie: ****\n");

        String saved = new String(Files.readAllBytes(cfg), StandardCharsets.UTF_8);
        assertFalse(saved.contains("****"));
        assertFalse(saved.contains("cookie"));
        assertEquals("server:\n  port: 8099\nfq:\n  api:\n", saved);
    }

    @Test
    void saveConfigFromYamlRejectsInvalidSyntaxWithIllegalArgumentExceptionAndDoesNotWrite() throws Exception {
        Path cfg = tempDir.resolve("application.yml");
        Files.write(cfg, "server:\n  port: 8099\n".getBytes(StandardCharsets.UTF_8));
        ConfigManagementService service = new ConfigManagementService();
        ReflectionTestUtils.setField(service, "configLocation", cfg.toString());

        try {
            service.saveConfigFromYaml("fq:\n  api: [unclosed\n");
            org.junit.jupiter.api.Assertions.fail("应当抛出 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("YAML"));
        }

        // 校验失败不得写入：磁盘内容保持原样
        String saved = new String(Files.readAllBytes(cfg), StandardCharsets.UTF_8);
        assertEquals("server:\n  port: 8099\n", saved);
    }
}
