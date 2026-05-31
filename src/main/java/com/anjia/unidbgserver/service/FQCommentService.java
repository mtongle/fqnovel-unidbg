package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.*;
import com.anjia.unidbgserver.utils.FQApiUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.zip.GZIPInputStream;

@Slf4j
@Service
public class FQCommentService {

    @Resource(name = "fqEncryptWorker")
    private FQEncryptServiceWorker fqEncryptServiceWorker;

    @Resource
    private FQApiUtils fqApiUtils;

    @Resource
    private DevicePoolService devicePoolService;

    @Resource
    private RestTemplate restTemplate;

    @Resource
    private ObjectMapper objectMapper;

    public CompletableFuture<FQNovelResponse<JsonNode>> getCommentIdeaList(FQCommentIdeaRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            DeviceInfo device = devicePoolService.nextDevice();
            String path = "/novel/commentapi/idea/list/" + request.getChapterId() + "/v1";
            Map<String, String> queryParams = fqApiUtils.buildCommonApiParams(new FqVariable(device));

            Map<String, Object> businessParam = new LinkedHashMap<>();
            if (request.getBookId() != null && !request.getBookId().trim().isEmpty()) {
                businessParam.put("book_id", request.getBookId());
            }

            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("chapter_id", request.getChapterId());
            bodyMap.put("item_version", "1");
            bodyMap.put("comment_source", request.getCommentSource() != null ? request.getCommentSource() : 2);
            bodyMap.put("group_type", request.getGroupType() != null ? request.getGroupType() : 15);
            if (!businessParam.isEmpty()) {
                bodyMap.put("business_param", businessParam);
            }
            return executeCommentPost(path, queryParams, bodyMap, "段评统计", request.getChapterId());
        });
    }

    public CompletableFuture<FQNovelResponse<JsonNode>> getCommentList(FQCommentListRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            DeviceInfo device = devicePoolService.nextDevice();
            String path = "/novel/commentapi/comment/list/" + request.getChapterId() + "/v1";
            Map<String, String> queryParams = fqApiUtils.buildCommonApiParams(new FqVariable(device));

            Map<String, Object> businessParam = new LinkedHashMap<>();
            businessParam.put("book_id", request.getBookId());
            businessParam.put("item_version", "1");
            if (request.getParaIndex() != null) {
                businessParam.put("para_index", request.getParaIndex());
            }

            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("business_param", businessParam);
            bodyMap.put("comment_source", request.getCommentSource() != null ? request.getCommentSource() : 2);
            bodyMap.put("group_type", request.getGroupType() != null ? request.getGroupType() : 15);
            bodyMap.put("count", request.getCount() != null ? request.getCount() : 20);
            bodyMap.put("comment_type", request.getCommentType() != null ? request.getCommentType() : 1);
            if (request.getCursor() != null && !request.getCursor().isEmpty()) {
                bodyMap.put("cursor", request.getCursor());
            }

            return executeCommentPost(path, queryParams, bodyMap, "段评详情", request.getChapterId());
        });
    }

    public CompletableFuture<FQNovelResponse<JsonNode>> getReplyList(FQCommentReplyListRequest request) {
        return CompletableFuture.supplyAsync(() -> {
            DeviceInfo device = devicePoolService.nextDevice();
            String path = "/novel/commentapi/reply/list/" + request.getCommentId() + "/v1";
            Map<String, String> queryParams = fqApiUtils.buildCommonApiParams(new FqVariable(device));

            Map<String, Object> businessParam = new LinkedHashMap<>();
            businessParam.put("book_id", request.getBookId());
            businessParam.put("need_count", true);

            Map<String, Object> bodyMap = new LinkedHashMap<>();
            bodyMap.put("comment_id", request.getCommentId());
            bodyMap.put("group_id", request.getChapterId());
            bodyMap.put("group_type", request.getGroupType() != null ? request.getGroupType() : 15);
            bodyMap.put("comment_source", request.getCommentSource() != null ? request.getCommentSource() : 502);
            bodyMap.put("business_param", businessParam);
            bodyMap.put("count", request.getCount() != null ? request.getCount() : 20);
            if (request.getCursor() != null && !request.getCursor().isEmpty()) {
                bodyMap.put("cursor", request.getCursor());
            }

            return executeCommentPost(path, queryParams, bodyMap, "段评回复列表", request.getCommentId());
        });
    }

    private FQNovelResponse<JsonNode> executeCommentPost(
            String path,
            Map<String, String> queryParams,
            Map<String, Object> bodyMap,
            String apiLabel,
            String chapterId) {

        int maxAttempts = Math.max(2, devicePoolService.getTargetPoolSize() + 1);
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            DeviceInfo currentDevice = devicePoolService.nextDevice();
            try {
                String baseUrl = fqApiUtils.getBaseUrl();
                String url = baseUrl + path;
                String fullUrl = fqApiUtils.buildUrlWithParams(url, queryParams);

                String body = objectMapper.writeValueAsString(bodyMap);

                Map<String, String> headers = fqApiUtils.buildCommonHeaders(currentDevice);
                headers.put("Content-Type", "application/json");
                Map<String, String> signedHeaders = fqEncryptServiceWorker.generateSignatureHeaders(fullUrl, headers).get();

                Object commentSource = bodyMap.get("comment_source");

                HttpHeaders httpHeaders = new HttpHeaders();
                signedHeaders.forEach(httpHeaders::set);
                headers.forEach(httpHeaders::set);
                if (commentSource != null) {
                    httpHeaders.set("comment-source", String.valueOf(commentSource));
                }
                httpHeaders.set("server-channel", "17");

                HttpEntity<String> entity = new HttpEntity<>(body, httpHeaders);
                log.warn("段评请求 - url={}, headers={}, body={}", fullUrl, httpHeaders, body);
                ResponseEntity<byte[]> response = restTemplate.exchange(fullUrl, HttpMethod.POST, entity, byte[].class);

                String responseBody = decompressGzipResponse(response.getBody());
                if (responseBody.trim().isEmpty()) {
                    throw new RuntimeException("EMPTY_RESPONSE");
                }

                JsonNode jsonResponse = objectMapper.readTree(responseBody);
                return FQNovelResponse.success(jsonResponse);
            } catch (Exception e) {
                if (isEmptyResponseError(e)) {
                    log.warn("{}接口空响应，剔除并补充设备后重试。attempt={}/{}, deviceId={}",
                        apiLabel, attempt, maxAttempts,
                        currentDevice != null ? currentDevice.getDeviceId() : null);
                    devicePoolService.removeAndReplenish(currentDevice, apiLabel + " empty response");
                    continue;
                }
                log.error("获取{}失败 - chapterId: {}, attempt={}", apiLabel, chapterId, attempt, e);
                return FQNovelResponse.error("获取" + apiLabel + "失败: " + e.getMessage());
            }
        }
        return FQNovelResponse.error("获取" + apiLabel + "失败: 设备池重试后仍为空响应");
    }

    private String decompressGzipResponse(byte[] gzipData) throws Exception {
        if (gzipData == null || gzipData.length == 0) {
            return "";
        }
        boolean isGzip = gzipData.length >= 2
            && gzipData[0] == (byte) 0x1f
            && gzipData[1] == (byte) 0x8b;

        if (!isGzip) {
            return new String(gzipData, StandardCharsets.UTF_8);
        }
        try (GZIPInputStream gzipInputStream = new GZIPInputStream(new ByteArrayInputStream(gzipData))) {
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int length;
            while ((length = gzipInputStream.read(buffer)) != -1) {
                byteArrayOutputStream.write(buffer, 0, length);
            }
            return new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private boolean isEmptyResponseError(Exception e) {
        String message = e.getMessage();
        return "EMPTY_RESPONSE".equals(message)
            || (message != null && message.contains("No content to map due to end-of-input"));
    }
}
