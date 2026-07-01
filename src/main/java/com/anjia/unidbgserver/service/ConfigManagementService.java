package com.anjia.unidbgserver.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
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
     * 保存 YAML 字符串到配置文件（会先验证 YAML 合法性）
     */
    public void saveConfigFromYaml(String yamlContent) throws IOException {
        // 使用 SafeConstructor 防止 SnakeYAML 反序列化 RCE（CVE-2022-1471）
        Yaml yaml = new Yaml(new SafeConstructor());
        Object loaded = yaml.load(yamlContent);
        if (loaded == null) {
            throw new IllegalArgumentException("YAML 内容为空或无效");
        }
        String path = getConfigFilePath();
        Files.write(Paths.get(path), yamlContent.getBytes());
        log.info("配置已保存至 {}", path);
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
