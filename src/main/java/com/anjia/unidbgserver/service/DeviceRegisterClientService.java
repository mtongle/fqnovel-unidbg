package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.DeviceInfo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.zip.GZIPInputStream;

/**
 * 设备注册客户端（Java复刻版）
 * 对应 tools/batch_device_register_xml.py 中 EnhancedDeviceRegisterClient 的核心逻辑
 */
@Slf4j
@Service
public class DeviceRegisterClientService {

    private static final String REGISTER_URL = "https://log5-applog.fqnovel.com/service/2/device_register/";
    private static final List<String> ROM_VALUES = Arrays.asList("1414", "1415", "1416", "1417", "1418", "1419", "1420");

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean registerDevice(DeviceInfo deviceInfo) {
        if (deviceInfo == null) {
            return false;
        }

        normalizeDeviceInfo(deviceInfo);

        String registerUrl = buildRegisterUrl(deviceInfo);
        HttpHeaders headers = buildHeaders(deviceInfo);
        Map<String, Object> payload = buildPayload(deviceInfo);

        try {
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(payload, headers);
            ResponseEntity<byte[]> response = restTemplate.exchange(registerUrl, HttpMethod.POST, entity, byte[].class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                log.warn("device_register失败，状态码: {}", response.getStatusCodeValue());
                return false;
            }

            String body = decodeResponseBody(response);
            if (body != null && !body.trim().isEmpty()) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(body);
                    if (jsonNode.has("device_id")) {
                        deviceInfo.setDeviceId(jsonNode.get("device_id").asText(deviceInfo.getDeviceId()));
                    }
                    if (jsonNode.has("install_id")) {
                        deviceInfo.setInstallId(jsonNode.get("install_id").asText(deviceInfo.getInstallId()));
                    }
                } catch (Exception parseException) {
                    String preview = body.length() > 160 ? body.substring(0, 160) + "..." : body;
                    log.debug("device_register响应非JSON，继续使用本地设备ID。preview={}", preview);
                }
            }

            deviceInfo.setCookie(String.format("store-region=cn-zj; store-region-src=did; install_id=%s;", deviceInfo.getInstallId()));
            return true;
        } catch (Exception e) {
            log.warn("device_register请求失败 - brand: {}, type: {}", deviceInfo.getDeviceBrand(), deviceInfo.getDeviceType(), e);
            return false;
        }
    }

    private String decodeResponseBody(ResponseEntity<byte[]> response) {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) {
            return "";
        }

        boolean gzipEncoded = false;
        List<String> contentEncoding = response.getHeaders().get("Content-Encoding");
        if (contentEncoding != null) {
            gzipEncoded = contentEncoding.stream().anyMatch(v -> v != null && v.toLowerCase(Locale.ROOT).contains("gzip"));
        }
        if (!gzipEncoded && body.length >= 2 && body[0] == (byte) 0x1f && body[1] == (byte) 0x8b) {
            gzipEncoded = true;
        }

        if (!gzipEncoded) {
            return new String(body, StandardCharsets.UTF_8);
        }

        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(body))) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = gzipInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, length);
            }
            return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("device_register响应解压失败，回退原始文本解析", e);
            return new String(body, StandardCharsets.UTF_8);
        }
    }

    private HttpHeaders buildHeaders(DeviceInfo deviceInfo) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
        headers.set("Accept-Encoding", "gzip");
        headers.set("log-encode-type", "gzip");
        headers.set("x-ss-req-ticket", String.valueOf(deviceInfo.getGenTime()));
        headers.set("x-vc-bdturing-sdk-version", "3.7.2.cn");
        headers.set("User-Agent", deviceInfo.getUserAgent());
        headers.set("Cookie", deviceInfo.getCookie());
        return headers;
    }

    private String buildRegisterUrl(DeviceInfo deviceInfo) {
        return UriComponentsBuilder
            .fromHttpUrl(REGISTER_URL)
            .queryParam("aid", "1967")
            .queryParam("version_code", "68132")
            .queryParam("channel", "googleplay")
            .queryParam("package", "com.dragon.read.oversea.gp")
            .queryParam("_rticket", String.valueOf(deviceInfo.getRticket()))
            .queryParam("use_store_region_cookie", "1")
            .queryParam("okhttp_version", "4.2.137.76-fanqie")
            .build(false)
            .toUriString();
    }

    private Map<String, Object> buildPayload(DeviceInfo deviceInfo) {
        Map<String, Object> header = buildAppInfo();

        header.put("os", "Android");
        header.put("os_version", deviceInfo.getOsVersion());
        header.put("os_api", deviceInfo.getOsApi());
        header.put("device_model", deviceInfo.getDeviceType());
        header.put("device_brand", deviceInfo.getDeviceBrand());
        header.put("device_manufacturer", deviceInfo.getDeviceManufacturer());
        header.put("cpu_abi", deviceInfo.getCpuAbi());
        header.put("release_build", deviceInfo.getReleaseBuild());
        header.put("density_dpi", parseInteger(deviceInfo.getDpi(), 320));
        header.put("display_density", defaultString(deviceInfo.getDisplayDensity(), "xxhdpi"));
        header.put("resolution", defaultString(deviceInfo.getResolution(), "1600*900").replace("*", "x"));
        header.put("language", "zh");
        header.put("timezone", 8);
        header.put("access", "wifi");
        header.put("rom", defaultString(deviceInfo.getRom(), randomRom()));
        header.put("rom_version", defaultString(deviceInfo.getRomVersion(), "V433IR+release-keys").replace("+", " "));
        header.put("cdid", deviceInfo.getCdid());
        header.put("sig_hash", deviceInfo.getSigHash());
        header.put("openudid", deviceInfo.getOpenudid());
        header.put("clientudid", deviceInfo.getClientudid());

        Map<String, Object> ipv6Map = new HashMap<>();
        ipv6Map.put("type", "client_anpi");
        ipv6Map.put("value", deviceInfo.getIpv6Address());
        header.put("ipv6_list", Collections.singletonList(ipv6Map));

        header.put("region", "CN");
        header.put("tz_name", "Asia/Shanghai");
        header.put("tz_offset", 28800);
        header.put("sim_serial_number", Collections.emptyList());
        header.put("oaid_may_support", false);
        header.put("req_id", deviceInfo.getReqId());
        header.put("device_platform", "android");

        Map<String, Object> custom = new HashMap<>();
        custom.put("host_bit", 64);
        custom.put("account_region", "cn");
        custom.put("dragon_device_type", "phone");
        header.put("custom", custom);

        header.put("apk_first_install_time", deviceInfo.getApkFirstInstallTime());

        Map<String, Object> payload = new HashMap<>();
        payload.put("magic_tag", "ss_app_log");
        payload.put("header", header);
        payload.put("_gen_time", deviceInfo.getGenTime());
        return payload;
    }

    private Map<String, Object> buildAppInfo() {
        Map<String, Object> appInfo = new HashMap<>();
        appInfo.put("display_name", "番茄小说");
        appInfo.put("aid", 1967);
        appInfo.put("channel", "googleplay");
        appInfo.put("package", "com.dragon.read.oversea.gp");
        appInfo.put("app_version", "6.8.1.32");
        appInfo.put("version_code", 68132);
        appInfo.put("update_version_code", 68132);
        appInfo.put("manifest_version_code", 68132);
        appInfo.put("app_version_minor", "6.8.1.32");
        appInfo.put("sdk_version", "3.7.0-rc.25-fanqie-xiaoshuo-opt");
        appInfo.put("sdk_target_version", 29);
        appInfo.put("git_hash", "5b6a0d3");
        appInfo.put("sdk_flavor", "china");
        appInfo.put("guest_mode", 0);
        appInfo.put("is_system_app", 0);
        appInfo.put("pre_installed_channel", "");
        appInfo.put("not_request_sender", 0);
        return appInfo;
    }

    private void normalizeDeviceInfo(DeviceInfo deviceInfo) {
        long now = System.currentTimeMillis();

        if (isBlank(deviceInfo.getAndroidId())) {
            deviceInfo.setAndroidId(randomHex(16));
        }
        if (isBlank(deviceInfo.getOpenudid())) {
            deviceInfo.setOpenudid(generateOpenudid(deviceInfo.getAndroidId()));
        }
        if (isBlank(deviceInfo.getClientudid())) {
            deviceInfo.setClientudid(UUID.randomUUID().toString());
        }
        if (isBlank(deviceInfo.getSigHash())) {
            deviceInfo.setSigHash(randomHex(32));
        }
        if (isBlank(deviceInfo.getIpv6Address())) {
            deviceInfo.setIpv6Address(randomIpv6());
        }
        if (isBlank(deviceInfo.getDeviceManufacturer())) {
            deviceInfo.setDeviceManufacturer(defaultString(deviceInfo.getDeviceBrand(), "OnePlus"));
        }
        if (isBlank(deviceInfo.getCpuAbi())) {
            deviceInfo.setCpuAbi(defaultString(deviceInfo.getHostAbi(), "arm64-v8a"));
        }
        if (isBlank(deviceInfo.getReqId())) {
            deviceInfo.setReqId(UUID.randomUUID().toString());
        }
        if (isBlank(deviceInfo.getRom())) {
            deviceInfo.setRom(randomRom());
        }
        if (deviceInfo.getGenTime() == null) {
            deviceInfo.setGenTime(now);
        }
        if (deviceInfo.getRticket() == null) {
            deviceInfo.setRticket(now);
        }
        if (deviceInfo.getApkFirstInstallTime() == null) {
            long installTime = now - ThreadLocalRandom.current().nextLong(86400000L, 31536000000L);
            deviceInfo.setApkFirstInstallTime(installTime);
        }
        if (isBlank(deviceInfo.getOsVersion())) {
            deviceInfo.setOsVersion("13");
        }
        if (deviceInfo.getOsApi() == null) {
            deviceInfo.setOsApi(33);
        }
        if (isBlank(deviceInfo.getDeviceBrand())) {
            deviceInfo.setDeviceBrand("OnePlus");
        }
        if (isBlank(deviceInfo.getDeviceType())) {
            deviceInfo.setDeviceType("PJZ110");
        }
        if (isBlank(deviceInfo.getResolution())) {
            deviceInfo.setResolution("1600*900");
        }
        if (isBlank(deviceInfo.getDpi())) {
            deviceInfo.setDpi("320");
        }
        if (isBlank(deviceInfo.getRomVersion())) {
            deviceInfo.setRomVersion("V433IR+release-keys");
        }
        if (isBlank(deviceInfo.getReleaseBuild())) {
            String release = deviceInfo.getRomVersion().split("\\+")[0];
            deviceInfo.setReleaseBuild(release + "_20171120");
        }
        if (isBlank(deviceInfo.getDeviceId())) {
            deviceInfo.setDeviceId(randomNumeric(16));
        }
        if (isBlank(deviceInfo.getInstallId())) {
            deviceInfo.setInstallId(randomNumeric(16));
        }
        if (isBlank(deviceInfo.getCdid())) {
            deviceInfo.setCdid(UUID.randomUUID().toString());
        }
        if (isBlank(deviceInfo.getCookie())) {
            deviceInfo.setCookie(String.format("store-region=cn-zj; store-region-src=did; install_id=%s;", deviceInfo.getInstallId()));
        }
        if (isBlank(deviceInfo.getUserAgent())) {
            String release = defaultString(deviceInfo.getRomVersion(), "V433IR+release-keys").split("\\+")[0];
            deviceInfo.setUserAgent(String.format(
                "com.dragon.read.oversea.gp/68132 (Linux; U; Android %s; zh_CN; %s; Build/%s;tt-ok/3.12.13.4-tiktok)",
                deviceInfo.getOsVersion(),
                deviceInfo.getDeviceType(),
                release
            ));
        }
        if (isBlank(deviceInfo.getAid())) {
            deviceInfo.setAid("1967");
        }
        if (isBlank(deviceInfo.getVersionCode())) {
            deviceInfo.setVersionCode("68132");
        }
        if (isBlank(deviceInfo.getVersionName())) {
            deviceInfo.setVersionName("6.8.1.32");
        }
        if (isBlank(deviceInfo.getUpdateVersionCode())) {
            deviceInfo.setUpdateVersionCode("68132");
        }
    }

    private int parseInteger(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (Exception ignore) {
            return defaultValue;
        }
    }

    private String randomRom() {
        return ROM_VALUES.get(ThreadLocalRandom.current().nextInt(ROM_VALUES.size()));
    }

    private String randomNumeric(int length) {
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            int digit = ThreadLocalRandom.current().nextInt(10);
            if (i == 0 && digit == 0) {
                digit = 1;
            }
            sb.append(digit);
        }
        return sb.toString();
    }

    private String randomHex(int length) {
        String chars = "0123456789abcdef";
        StringBuilder sb = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            sb.append(chars.charAt(ThreadLocalRandom.current().nextInt(chars.length())));
        }
        return sb.toString();
    }

    private String randomIpv6() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 8; i++) {
            if (i > 0) {
                sb.append(":");
            }
            sb.append(String.format("%04X", ThreadLocalRandom.current().nextInt(0, 65536)));
        }
        return sb.toString();
    }

    private String generateOpenudid(String androidId) {
        String md5_1 = md5(androidId);
        String md5_2 = md5(md5_1);
        return (md5_1 + md5_2.substring(0, 8)).toLowerCase(Locale.ROOT);
    }

    private String md5(String text) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return text;
        }
    }

    private String defaultString(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
