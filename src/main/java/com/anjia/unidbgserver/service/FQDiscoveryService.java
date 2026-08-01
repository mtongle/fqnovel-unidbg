package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.*;
import com.anjia.unidbgserver.utils.CommonUtils;
import com.anjia.unidbgserver.utils.FQApiUtils;
import com.anjia.unidbgserver.utils.GzipUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * FQ小说发现与分类服务
 * 基于APK真实接口:
 *   - /reading/bookapi/new_category/front/v  — 分类发现页
 *   - /reading/bookapi/new_category/landing/v — 分类下书籍
 *   - /reading/bookapi/new_category/cell/v    — 分类cell数据
 */
@Slf4j
@Service
public class FQDiscoveryService {

    @Resource(name = "fqEncryptWorker")
    private FQEncryptServiceWorker fqEncryptServiceWorker;

    @Resource
    private FQApiUtils fqApiUtils;

    @Resource
    private DevicePoolService devicePoolService;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public FQDiscoveryService(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 获取分类发现页
     * 返回热门分类、下拉分类列表、Tab配置与内容
     */
    public CompletableFuture<FQNovelResponse<FQCategoryFrontResponse>> getCategoryFrontPage(
            Integer categoryTab, Boolean needHot, String source) {
        return CompletableFuture.supplyAsync(() -> {
            int maxAttempts = Math.max(2, devicePoolService.getTargetPoolSize() + 1);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                DeviceInfo currentDevice = devicePoolService.nextDevice();
                try {
                    String url = fqApiUtils.getBaseUrl() + "/reading/bookapi/new_category/front/v";
                    Map<String, String> params = buildFrontPageParams(currentDevice, categoryTab, needHot, source);
                    String fullUrl = fqApiUtils.buildUrlWithParams(url, params);

                    Map<String, String> headers = fqApiUtils.buildCommonHeaders(currentDevice);
                    Map<String, String> signedHeaders = fqEncryptServiceWorker
                            .generateSignatureHeaders(fullUrl, headers).get();

                    // 签名失败时返回 {"error": ...}，不能当 HTTP header 静默发出
                    if (signedHeaders.containsKey("error")) {
                        log.warn("分类发现页签名生成失败: {}", signedHeaders.get("error"));
                        throw new RuntimeException("签名生成失败: " + signedHeaders.get("error"));
                    }

                    HttpHeaders httpHeaders = new HttpHeaders();
                    signedHeaders.forEach(httpHeaders::set);
                    headers.forEach(httpHeaders::set);

                    HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
                    ResponseEntity<byte[]> response = restTemplate.exchange(
                            fullUrl, HttpMethod.GET, entity, byte[].class);

                    String responseBody = decodeResponseBody(response);
                    if (responseBody.trim().isEmpty()) {
                        throw new RuntimeException("EMPTY_RESPONSE");
                    }

                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    JsonNode dataNode = rootNode.get("data");
                    FQCategoryFrontResponse result = new FQCategoryFrontResponse();
                    if (dataNode != null) {
                        result = objectMapper.treeToValue(dataNode, FQCategoryFrontResponse.class);
                    }
                    return FQNovelResponse.success(result);
                } catch (Exception e) {
                    if (isEmptyResponseError(e)) {
                        log.warn("分类发现页接口空响应，重试 attempt={}/{}", attempt, maxAttempts);
                        devicePoolService.removeAndReplenish(currentDevice, "category_front empty");
                        continue;
                    }
                    log.error("获取分类发现页失败", e);
                    return FQNovelResponse.error("获取分类发现页失败: " + e.getMessage());
                }
            }
            return FQNovelResponse.error("获取分类发现页失败: 设备池重试后仍为空响应");
        });
    }

    /**
     * 获取分类下书籍列表（分类落地页）
     */
    public CompletableFuture<FQNovelResponse<FQCategoryLandingResponse>> getCategoryLandingPage(
            String categoryId, int offset, int limit, String subCategoryId,
            String sortBy, String bookStatus, String wordNumber) {
        return CompletableFuture.supplyAsync(() -> {
            int maxAttempts = Math.max(2, devicePoolService.getTargetPoolSize() + 1);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                DeviceInfo currentDevice = devicePoolService.nextDevice();
                try {
                    String url = fqApiUtils.getBaseUrl() + "/reading/bookapi/new_category/landing/v";
                    Map<String, String> params = buildLandingPageParams(
                            currentDevice, categoryId, offset, limit,
                            subCategoryId, sortBy, bookStatus, wordNumber);
                    String fullUrl = fqApiUtils.buildUrlWithParams(url, params);

                    Map<String, String> headers = fqApiUtils.buildCommonHeaders(currentDevice);
                    Map<String, String> signedHeaders = fqEncryptServiceWorker
                            .generateSignatureHeaders(fullUrl, headers).get();

                    // 签名失败时返回 {"error": ...}，不能当 HTTP header 静默发出
                    if (signedHeaders.containsKey("error")) {
                        log.warn("分类落地页签名生成失败: {}", signedHeaders.get("error"));
                        throw new RuntimeException("签名生成失败: " + signedHeaders.get("error"));
                    }

                    HttpHeaders httpHeaders = new HttpHeaders();
                    signedHeaders.forEach(httpHeaders::set);
                    headers.forEach(httpHeaders::set);

                    HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
                    ResponseEntity<byte[]> response = restTemplate.exchange(
                            fullUrl, HttpMethod.GET, entity, byte[].class);

                    String responseBody = decodeResponseBody(response);
                    if (responseBody.trim().isEmpty()) {
                        throw new RuntimeException("EMPTY_RESPONSE");
                    }

                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    FQCategoryLandingResponse result = parseLandingResponse(rootNode);
                    return FQNovelResponse.success(result);
                } catch (Exception e) {
                    if (isEmptyResponseError(e)) {
                        log.warn("分类落地页接口空响应，重试 attempt={}/{}, categoryId={}",
                                attempt, maxAttempts, categoryId);
                        devicePoolService.removeAndReplenish(currentDevice, "category_landing empty");
                        continue;
                    }
                    log.error("获取分类落地页失败 - categoryId: {}", categoryId, e);
                    return FQNovelResponse.error("获取分类书籍列表失败: " + e.getMessage());
                }
            }
            return FQNovelResponse.error("获取分类书籍列表失败: 设备池重试后仍为空响应");
        });
    }

    /**
     * 获取分类Cell数据（更多内容区块）
     */
    public CompletableFuture<FQNovelResponse<FQCategoryCellResponse>> getCategoryCellData(
            int newCategoryTab, int reqType, int distinctStyle) {
        return CompletableFuture.supplyAsync(() -> {
            int maxAttempts = Math.max(2, devicePoolService.getTargetPoolSize() + 1);
            for (int attempt = 1; attempt <= maxAttempts; attempt++) {
                DeviceInfo currentDevice = devicePoolService.nextDevice();
                try {
                    String url = fqApiUtils.getBaseUrl() + "/reading/bookapi/new_category/cell/v";
                    Map<String, String> params = buildCellDataParams(
                            currentDevice, newCategoryTab, reqType, distinctStyle);
                    String fullUrl = fqApiUtils.buildUrlWithParams(url, params);

                    Map<String, String> headers = fqApiUtils.buildCommonHeaders(currentDevice);
                    Map<String, String> signedHeaders = fqEncryptServiceWorker
                            .generateSignatureHeaders(fullUrl, headers).get();

                    // 签名失败时返回 {"error": ...}，不能当 HTTP header 静默发出
                    if (signedHeaders.containsKey("error")) {
                        log.warn("分类Cell数据签名生成失败: {}", signedHeaders.get("error"));
                        throw new RuntimeException("签名生成失败: " + signedHeaders.get("error"));
                    }

                    HttpHeaders httpHeaders = new HttpHeaders();
                    signedHeaders.forEach(httpHeaders::set);
                    headers.forEach(httpHeaders::set);

                    HttpEntity<String> entity = new HttpEntity<>(httpHeaders);
                    ResponseEntity<byte[]> response = restTemplate.exchange(
                            fullUrl, HttpMethod.GET, entity, byte[].class);

                    String responseBody = decodeResponseBody(response);
                    if (responseBody.trim().isEmpty()) {
                        throw new RuntimeException("EMPTY_RESPONSE");
                    }

                    JsonNode rootNode = objectMapper.readTree(responseBody);
                    JsonNode dataNode = rootNode.get("data");
                    FQCategoryCellResponse result = new FQCategoryCellResponse();
                    if (dataNode != null) {
                        // cells 数据可能是复杂结构，保持原始JSON
                        if (dataNode.has("cells")) {
                            List<Object> cells = new ArrayList<>();
                            for (JsonNode cell : dataNode.get("cells")) {
                                cells.add(objectMapper.treeToValue(cell, Object.class));
                            }
                            result.setCells(cells);
                        }
                        if (dataNode.has("has_more")) {
                            result.setHasMore(dataNode.get("has_more").asBoolean());
                        }
                    }
                    return FQNovelResponse.success(result);
                } catch (Exception e) {
                    if (isEmptyResponseError(e)) {
                        log.warn("分类Cell数据接口空响应，重试 attempt={}/{}", attempt, maxAttempts);
                        devicePoolService.removeAndReplenish(currentDevice, "category_cell empty");
                        continue;
                    }
                    log.error("获取分类Cell数据失败", e);
                    return FQNovelResponse.error("获取分类Cell数据失败: " + e.getMessage());
                }
            }
            return FQNovelResponse.error("获取分类Cell数据失败: 设备池重试后仍为空响应");
        });
    }

    // ==================== 参数构建 ====================

    private Map<String, String> buildFrontPageParams(
            DeviceInfo device, Integer categoryTab, Boolean needHot, String source) {
        FqVariable var = new FqVariable(device);
        Map<String, String> params = fqApiUtils.buildCommonApiParams(var);
        // 可选参数，与APK中GetNewCategoryFrontPageRequest字段对应
        if (categoryTab != null) params.put("category_tab", String.valueOf(categoryTab));
        if (needHot != null)   params.put("need_hot", needHot ? "1" : "0");
        if (source != null)    params.put("source", source);
        params.put("update_version_code", var.getUpdateVersionCode());
        return params;
    }

    private Map<String, String> buildLandingPageParams(
            DeviceInfo device, String categoryId, int offset, int limit,
            String subCategoryId, String sortBy, String bookStatus, String wordNumber) {
        FqVariable var = new FqVariable(device);
        Map<String, String> params = fqApiUtils.buildCommonApiParams(var);
        if (categoryId != null && !categoryId.isEmpty()) {
            params.put("category_id", categoryId);
        }
        params.put("offset", String.valueOf(Math.max(offset, 0)));
        // limit 不能超过 50
        params.put("limit", String.valueOf(Math.min(Math.max(limit, 1), 50)));
        if (subCategoryId != null && !subCategoryId.isEmpty()) {
            params.put("sub_category_id", subCategoryId);
        }
        if (sortBy != null && !sortBy.isEmpty()) {
            params.put("sort_by", sortBy);
        }
        if (bookStatus != null && !bookStatus.isEmpty()) {
            params.put("book_status", bookStatus);
        }
        if (wordNumber != null && !wordNumber.isEmpty()) {
            params.put("word_number", wordNumber);
        }
        params.put("update_version_code", var.getUpdateVersionCode());
        return params;
    }

    private Map<String, String> buildCellDataParams(
            DeviceInfo device, int newCategoryTab, int reqType, int distinctStyle) {
        FqVariable var = new FqVariable(device);
        Map<String, String> params = fqApiUtils.buildCommonApiParams(var);
        params.put("new_category_tab", String.valueOf(newCategoryTab));
        params.put("req_type", String.valueOf(reqType));
        params.put("distinct_style", String.valueOf(distinctStyle));
        params.put("update_version_code", var.getUpdateVersionCode());
        return params;
    }

    // ==================== 响应解析 ====================

    /**
     * 解析落地页响应，将ApiBookInfo映射到FQSearchResponse.BookItem
     */
    private FQCategoryLandingResponse parseLandingResponse(JsonNode rootNode) throws Exception {
        JsonNode dataNode = rootNode.get("data");
        if (dataNode == null || dataNode.isNull()) {
            return new FQCategoryLandingResponse();
        }

        FQCategoryLandingResponse result = new FQCategoryLandingResponse();

        // 解析书籍列表
        JsonNode bookInfoNode = dataNode.get("book_info");
        if (bookInfoNode != null && bookInfoNode.isArray()) {
            List<FQSearchResponse.BookItem> books = new ArrayList<>();
            for (JsonNode bookNode : bookInfoNode) {
                FQSearchResponse.BookItem item = new FQSearchResponse.BookItem();
                item.setBookId(jsonStr(bookNode, "book_id"));
                item.setBookName(jsonStr(bookNode, "book_name"));
                item.setBookShortName(jsonStr(bookNode, "book_short_name"));
                item.setAuthor(jsonStr(bookNode, "author"));
                item.setAuthorId(jsonStr(bookNode, "author_id"));
                item.setDescription(jsonStr(bookNode, "abstract"));
                item.setBookAbstractV2(jsonStr(bookNode, "book_abstract_v2"));
                item.setCoverUrl(jsonStr(bookNode, "thumb_url"));
                item.setDetailPageThumbUrl(jsonStr(bookNode, "detail_page_thumb_url"));
                item.setExpandThumbUrl(jsonStr(bookNode, "expand_thumb_url"));
                item.setHorizThumbUrl(jsonStr(bookNode, "horiz_thumb_url"));
                item.setStatus(jsonStr(bookNode, "status"));
                item.setWordCount(jsonLong(bookNode, "word_number"));
                // 章节总数
                if (bookNode.has("serial_count")) {
                    try {
                        item.setTotalChapters(Integer.parseInt(jsonStr(bookNode, "serial_count")));
                    } catch (NumberFormatException e) {
                        log.warn("解析serial_count失败", e);
                    }
                } else if (bookNode.has("content_chapter_number")) {
                    try {
                        item.setTotalChapters(Integer.parseInt(jsonStr(bookNode, "content_chapter_number")));
                    } catch (NumberFormatException e) {
                        log.warn("解析content_chapter_number失败", e);
                    }
                }
                item.setLastChapterTitle(jsonStr(bookNode, "last_chapter_title"));
                item.setLastChapterItemId(jsonStr(bookNode, "last_chapter_item_id"));
                item.setCategory(jsonStr(bookNode, "category"));
                item.setCategoryV2(jsonStr(bookNode, "category_v2"));
                // 标签
                JsonNode tagsNode = bookNode.get("tags");
                if (tagsNode != null && tagsNode.isArray()) {
                    List<String> tags = new ArrayList<>();
                    for (JsonNode t : tagsNode) tags.add(t.asText());
                    item.setTags(tags);
                }
                item.setRating(bookNode.has("score") ? bookNode.get("score").asDouble() : 0);
                item.setReadCount(jsonStr(bookNode, "read_count"));
                item.setReadCntText(jsonStr(bookNode, "read_cnt_text"));
                item.setFreeStatus(jsonStr(bookNode, "free_status"));
                item.setVipBook(jsonStr(bookNode, "vip_book"));

                if (item.getBookId() != null && !item.getBookId().isEmpty()) {
                    books.add(item);
                }
            }
            result.setBooks(books);
        }

        // 分页信息
        if (dataNode.has("has_more")) result.setHasMore(dataNode.get("has_more").asBoolean());
        if (dataNode.has("offset"))   result.setOffset(dataNode.get("offset").asLong());
        if (dataNode.has("session_id")) result.setSessionId(jsonStr(dataNode, "session_id"));

        return result;
    }

    private String jsonStr(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asText("") : "";
    }

    private long jsonLong(JsonNode node, String field) {
        return node.has(field) ? node.get(field).asLong(0) : 0;
    }

    // ==================== 工具 ====================

    private String decodeResponseBody(ResponseEntity<byte[]> response) throws Exception {
        byte[] body = response.getBody();
        if (body == null || body.length == 0) return "";
        return GzipUtils.decodeBody(body, response.getHeaders().get("Content-Encoding"));
    }

    private boolean isEmptyResponseError(Exception e) {
        return CommonUtils.isEmptyResponseError(e);
    }
}
