package com.anjia.unidbgserver.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

/**
 * 设备信息DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceInfo {

    private String deviceBrand;
    private String deviceType;
    private String deviceId;
    private String installId;
    private String cdid;
    private String resolution;
    private String dpi;
    private String hostAbi;
    private String romVersion;
    private String osVersion;
    private Integer osApi;
    private String userAgent;
    private String cookie;
    private String aid;
    private String versionCode;
    private String versionName;
    private String updateVersionCode;

    private String androidId;
    private String openudid;
    private String clientudid;
    private String sigHash;
    private String ipv6Address;
    private String deviceManufacturer;
    private String cpuAbi;
    private String releaseBuild;
    private String displayDensity;
    private String rom;
    private String reqId;
    private Long apkFirstInstallTime;
    private Long genTime;
    private Long rticket;
}
