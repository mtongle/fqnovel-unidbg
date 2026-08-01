package com.anjia.unidbgserver.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * FQ API 配置
 *
 * 注意：设备相关参数（cdid/installId/deviceId/cookie 等）不再提供硬编码默认值，
 * 必须通过 application.yml / 环境变量注入。设备池模式（默认开启）会自动生成
 * 真实设备并写入配置，无需手工维护。
 */
@Data
@Component
@RefreshScope
@ConfigurationProperties(prefix = "fq.api")
public class FQApiProperties {

    /**
     * API基础URL
     */
    private String baseUrl = "https://api5-normal-sinfonlineb.fqnovel.com";

    /**
     * 默认User-Agent（设备池模式下由设备信息动态生成，此处为兜底）
     */
    private String userAgent = "";

    /**
     * Cookie配置（设备池模式下由设备注册动态生成，此处为兜底）
     */
    private String cookie = "";

    /**
     * 设备参数配置（设备池模式下由设备信息动态注入）
     */
    private Device device = new Device();

    /**
     * 段评API域名（commentapi路由可能不在海外版域名上，需按需配置）
     */
    private String commentApiBaseUrl = "https://api.fqnovel.com";

    /**
     * 搜索和目录API的替代域名后缀
     * 某些API需要用 api5-normal-sinfonlinec 替代默认的 api5-normal-sinfonlineb
     */
    private String searchApiBaseUrl = "https://api5-normal-sinfonlinec.fqnovel.com";

    /**
     * 设备池配置
     */
    private DevicePool devicePool = new DevicePool();

    @Data
    public static class Device {
        /**
         * 设备唯一标识符（设备池自动生成后回写）
         */
        private String cdid = "";

        /**
         * 安装ID（设备池自动生成后回写）
         */
        private String installId = "";

        /**
         * 设备ID（设备池自动生成后回写）
         */
        private String deviceId = "";

        /**
         * 应用ID
         */
        private String aid = "1967";

        /**
         * 版本代码
         */
        private String versionCode = "68132";

        /**
         * 版本名称
         */
        private String versionName = "6.8.1.32";

        /**
         * 更新版本代码
         */
        private String updateVersionCode = "68132";

        /**
         * 设备类型
         */
        private String deviceType = "";

        /**
         * 设备品牌
         */
        private String deviceBrand = "";

        /**
         * ROM版本
         */
        private String romVersion = "";

        /**
         * 分辨率
         */
        private String resolution = "";

        /**
         * DPI
         */
        private String dpi = "";

        /**
         * 主机ABI
         */
        private String hostAbi = "arm64-v8a";
    }

    @Data
    public static class DevicePool {
        /**
         * 是否启用设备池
         */
        private boolean enabled = true;

        /**
         * 设备池目标数量
         */
        private int size = 3;
    }
}
