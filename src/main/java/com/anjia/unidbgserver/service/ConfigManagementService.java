package com.anjia.unidbgserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置管理服务 - 读取/写入 application.yml 并支持热重载触发
 */
@Slf4j
@Service
public class ConfigManagementService {

    @Value("${spring.config.location:classpath:application.yml}")
    private String configLocation;

    private volatile String lastKnownConfigPath;

    /**
     * 获取当前配置文件路径（优先 classpath，回落到文件系统）
     */
    public String getConfigFilePath() {
        if (lastKnownConfigPath != null && Files.exists(Paths.get(lastKnownConfigPath))) {
            return lastKnownConfigPath;
        }
        if (configLocation.startsWith("classpath:")) {
            try {
                ClassPathResource resource = new ClassPathResource("application.yml");
                if (resource.exists()) {
                    File file = resource.getFile();
                    lastKnownConfigPath = file.getAbsolutePath();
                    return lastKnownConfigPath;
                }
            } catch (Exception e) {
                log.warn("无法通过 ClassPathResource 定位配置文件: {}", e.getMessage());
            }
            // 回落到项目源码目录
            String userDir = System.getProperty("user.dir");
            String candidate = userDir + "/src/main/resources/application.yml";
            if (Files.exists(Paths.get(candidate))) {
                lastKnownConfigPath = candidate;
                return candidate;
            }
            // 再回落当前目录
            candidate = "./application.yml";
            if (Files.exists(Paths.get(candidate))) {
                lastKnownConfigPath = candidate;
                return candidate;
            }
            throw new IllegalStateException("无法找到可写的 application.yml 配置文件");
        }
        // spring.config.location 可能带 file: 前缀（如 --spring.config.location=file:/opt/x/application.yml），
        // Paths.get 无法识别该前缀，必须先剥离，否则读写配置会抛 NoSuchFileException。
        if (configLocation.startsWith("file:")) {
            String filePath = configLocation.substring("file:".length());
            lastKnownConfigPath = filePath;
            return filePath;
        }
        lastKnownConfigPath = configLocation;
        return configLocation;
    }

    /**
     * 以字符串形式返回当前配置 YAML 内容
     */
    public String getConfigAsYaml() throws IOException {
        String path = getConfigFilePath();
        return new String(Files.readAllBytes(Paths.get(path)));
    }

    /**
     * 保存 YAML 字符串到配置文件（替换脱敏占位符后验证 YAML 合法性再写入）
     */
    public void saveConfigFromYaml(String yamlContent) throws IOException {
        String path = getConfigFilePath();
        String onDisk = "";
        Path configPath = Paths.get(path);
        if (Files.exists(configPath)) {
            onDisk = new String(Files.readAllBytes(configPath), StandardCharsets.UTF_8);
        }
        // 脱敏占位符( **** )永不落盘：值为占位符的条目用磁盘旧值替换，磁盘无此键则删除该行。
        // 注意：替换必须在 YAML 校验之前 —— SnakeYAML 将 **** 视为别名(alias)引用，扫描阶段即抛 ScannerException，
        // 只有先把占位符行替换/删除，剩余内容才能通过校验。校验对象即待写入文本，行为等价。
        String output = substituteRedactedPlaceholders(yamlContent, onDisk);
        // 使用 SafeConstructor 防止 SnakeYAML 反序列化 RCE（CVE-2022-1471）
        Yaml yaml = new Yaml(new SafeConstructor());
        Object loaded;
        try {
            loaded = yaml.load(output);
        } catch (Exception e) {
            // SnakeYAML 语法错误抛 ScannerException/ParserException（YamlException），
            // 统一转为 IllegalArgumentException，使 AdminController 走 400「YAML 格式无效」分支而非 500
            throw new IllegalArgumentException("YAML 语法错误: " + e.getMessage(), e);
        }
        if (loaded == null) {
            throw new IllegalArgumentException("YAML 内容为空或无效");
        }
        Files.write(configPath, output.getBytes(StandardCharsets.UTF_8));
        log.info("配置已保存至 {}", path);
    }

    /** 脱敏占位符标记（与 AdminController.redactSensitiveConfig 的脱敏输出一致） */
    static final String REDACTED_PLACEHOLDER = "****";

    /**
     * 保存前占位符替换：对提交文本中值恰为 {@value #REDACTED_PLACEHOLDER} 的行（含引号包裹形式），
     * 若磁盘当前配置存在同路径键，则整行替换为磁盘原始行（保留缩进与行内注释）；磁盘无此键则整行删除（不留空行）。
     * 其余行（未知键、注释、空行、顺序）逐字保留。
     * 注意：合法配置值字面量为 {@value #REDACTED_PLACEHOLDER} 的场景视为占位符处理（设计取舍）。
     */
    static String substituteRedactedPlaceholders(String incomingYaml, String currentOnDiskYaml) {
        Map<String, String> diskLeafLines = indexLeafLines(currentOnDiskYaml == null ? "" : currentOnDiskYaml);
        StringBuilder out = new StringBuilder(incomingYaml.length());
        // 以列表收集输出行，删除占位符行时不产生空行；最后以 \n 连接，保留原始换行结构（含尾随换行）
        List<String> outLines = new ArrayList<>();
        List<Object[]> stack = new ArrayList<>(); // 传入行自身的缩进栈，保证 keyPath 与磁盘一致
        for (String raw : incomingYaml.split("\n", -1)) {
            ParsedLine p = parseKeyValueLine(raw, stack);
            if (p != null && p.keyPath != null && REDACTED_PLACEHOLDER.equals(p.value)) {
                String diskLine = diskLeafLines.get(p.keyPath);
                if (diskLine != null) {
                    outLines.add(diskLine); // 用磁盘旧行整体替换（含缩进与行内注释）
                }
                // 磁盘无此键：整行删除
            } else {
                outLines.add(raw);
            }
        }
        for (int i = 0; i < outLines.size(); i++) {
            if (i > 0) out.append('\n');
            out.append(outLines.get(i));
        }
        return out.toString();
    }

    /** 解析单行 YAML 键值；keyPath 由调用方通过缩进栈计算 */
    private static final class ParsedLine {
        int indent;
        String key;      // 去引号后的键
        String keyPath;  // 点路径（缩进感知，与前端 cfgParseLines 规则一致）
        String value;    // 去引号后的值（无值为 ""）
        boolean isBlock; // 值为 | 或 > 开头的块标量
        boolean isLeaf;  // 真实叶键（排除 value 为空且未加引号的父级映射行）
    }

    /**
     * 将磁盘配置按缩进栈解析为 点路径 -> 原始行 映射（仅真实叶键；块标量行不计入）。
     * 键路径规则与前端 admin/config.html 的 cfgParseLines/cfgSplitKeyValue 保持一致。
     */
    private static Map<String, String> indexLeafLines(String yamlText) {
        Map<String, String> map = new HashMap<>();
        List<Object[]> stack = new ArrayList<>(); // {indent, path}
        String[] lines = yamlText.split("\n", -1);
        for (String raw : lines) {
            ParsedLine p = parseKeyValueLine(raw, stack);
            if (p != null && p.keyPath != null && p.isLeaf && !p.isBlock) {
                map.put(p.keyPath, raw);
            }
        }
        return map;
    }

    /**
     * 解析一行：返回 null 表示非键值行（空行/注释/无冒号/空键）。
     * 缩进栈（{indent, path}）与前端一致：同级或更浅的键弹出栈顶，父路径取栈顶。
     */
    private static ParsedLine parseKeyValueLine(String raw, List<Object[]> stack) {
        int indent = 0;
        while (indent < raw.length() && raw.charAt(indent) == ' ') indent++;
        String trimmed = raw.substring(Math.min(indent, raw.length())).trim();
        if (trimmed.isEmpty() || trimmed.charAt(0) == '#') return null;
        int colon = trimmed.indexOf(':');
        if (colon <= 0) return null;
        String keyRaw = trimmed.substring(0, colon).trim();
        if (keyRaw.isEmpty()) return null;
        String key = stripQuotes(keyRaw);
        String rest = trimmed.substring(colon + 1).trim();
        ParsedLine p = new ParsedLine();
        p.indent = indent;
        p.key = key;
        if (rest.isEmpty()) {
            p.value = "";
            p.isLeaf = false;
        } else if (rest.charAt(0) == '"' || rest.charAt(0) == '\'') {
            char q = rest.charAt(0);
            int end = rest.indexOf(q, 1);
            p.value = end >= 0 ? rest.substring(1, end) : rest.substring(1);
        } else {
            int ci = indexOfInlineComment(rest);
            p.value = (ci >= 0 ? rest.substring(0, ci) : rest).trim();
            p.isBlock = p.value.startsWith("|") || p.value.startsWith(">");
            p.isLeaf = !p.value.isEmpty();
        }
        if (stack != null) {
            while (!stack.isEmpty() && (Integer) stack.get(stack.size() - 1)[0] >= indent) {
                stack.remove(stack.size() - 1);
            }
            String parentPath = stack.isEmpty() ? "" : (String) stack.get(stack.size() - 1)[1];
            p.keyPath = parentPath.isEmpty() ? key : parentPath + "." + key;
            stack.add(new Object[]{indent, p.keyPath});
        } else {
            p.keyPath = key;
        }
        return p;
    }

    /** 去掉键的引号包裹 */
    private static String stripQuotes(String keyRaw) {
        if (keyRaw.length() >= 2) {
            char c0 = keyRaw.charAt(0);
            if ((c0 == '"' || c0 == '\'') && keyRaw.charAt(keyRaw.length() - 1) == c0) {
                return keyRaw.substring(1, keyRaw.length() - 1);
            }
        }
        return keyRaw;
    }

    /** 找行内注释起始位置（# 前有空格，与 YAML 语义一致）；无则 -1 */
    private static int indexOfInlineComment(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == '#' && (i == 0 || s.charAt(i - 1) == ' ' || s.charAt(i - 1) == '\t')) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 加载配置为 Map 对象
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> loadConfigAsMap() throws IOException {
        // 使用 SafeConstructor 防止 SnakeYAML 反序列化 RCE
        Yaml yaml = new Yaml(new SafeConstructor());
        try (InputStream is = new FileInputStream(getConfigFilePath())) {
            return yaml.load(is);
        }
    }

    /**
     * 保存 Map 对象到 YAML 配置文件
     */
    public void saveConfigFromMap(Map<String, Object> configMap) throws IOException {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        String path = getConfigFilePath();
        try (FileWriter writer = new FileWriter(path)) {
            yaml.dump(configMap, writer);
        }
        log.info("配置已保存至 {}", path);
    }
}
