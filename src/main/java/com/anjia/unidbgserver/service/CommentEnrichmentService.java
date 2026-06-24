package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.FQCommentIdeaRequest;
import com.anjia.unidbgserver.dto.FQNovelChapterInfo;
import com.anjia.unidbgserver.dto.FQNovelResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 段评内容增强服务
 * 在章节正文中插入段评图标，点击图标跳转到段评展示页
 */
@Slf4j
@Service
public class CommentEnrichmentService {

    @Resource
    private FQCommentService fqCommentService;

    /** 段评页面路径 */
    private static final String COMMENT_PAGE_PATH = "/api/ssr/comment-page";

    /**
     * 增强章节内容：查询段评统计，在有评论的段落后插入段评图标
     *
     * @param chapterResponse 原始章节响应
     * @param bookId 书籍ID
     * @param chapterId 章节ID
     * @return 增强后的章节内容
     */
    public CompletableFuture<FQNovelResponse<FQNovelChapterInfo>> enrichChapter(
            FQNovelResponse<FQNovelChapterInfo> chapterResponse,
            String bookId,
            String chapterId) {

        // 如果原始响应失败，直接返回
        if (chapterResponse.getCode() != 0 || chapterResponse.getData() == null) {
            return CompletableFuture.completedFuture(chapterResponse);
        }

        String txtContent = chapterResponse.getData().getTxtContent();
        if (txtContent == null || txtContent.trim().isEmpty()) {
            return CompletableFuture.completedFuture(chapterResponse);
        }

        // 异步调用段评统计 API
        FQCommentIdeaRequest ideaRequest = new FQCommentIdeaRequest();
        ideaRequest.setChapterId(chapterId);
        ideaRequest.setBookId(bookId);

        return fqCommentService.getCommentIdeaList(ideaRequest)
                .orTimeout(10, TimeUnit.SECONDS)
                .thenApply(ideaResponse -> {
                    try {
                        Map<Integer, Integer> commentCounts = parseIdeaResponse(ideaResponse);
                        if (commentCounts.isEmpty()) {
                            log.debug("章节 {} 无段评数据，不注入图标", chapterId);
                            return chapterResponse;
                        }

                        String title = chapterResponse.getData().getTitle();
                        String enrichedContent = injectCommentIcons(
                                txtContent, commentCounts, bookId, chapterId, title);
                        chapterResponse.getData().setTxtContent(enrichedContent);
                        log.info("章节 {} 段评增强完成，共 {} 个段落有评论",
                                chapterId, commentCounts.size());
                    } catch (Exception e) {
                        log.warn("段评增强失败，返回原始内容 - chapterId: {}", chapterId, e);
                    }
                    return chapterResponse;
                })
                .exceptionally(e -> {
                    log.warn("段评统计接口调用失败，返回原始内容 - chapterId: {}", chapterId, e);
                    return chapterResponse;
                });
    }

    /**
     * 解析段评统计接口响应，提取段落索引 -> 评论数 映射
     *
     * 实际返回格式:
     * {
     *   "data": {
     *     "data": {
     *       "data": {
     *         "0": { "count": 221, ... },   // key=段落索引, value.count=评论数
     *         "1": { "count": 175, ... },
     *       }
     *     }
     *   }
     * }
     */
    private Map<Integer, Integer> parseIdeaResponse(FQNovelResponse<JsonNode> ideaResponse) {
        Map<Integer, Integer> result = new HashMap<>();
        if (ideaResponse == null || ideaResponse.getData() == null) {
            return result;
        }

        try {
            JsonNode data = ideaResponse.getData();
            // 路径: data.data.data -> { "0": {...}, "1": {...}, ... }
            JsonNode paraMap = null;
            if (data.has("data") && data.get("data").has("data")) {
                paraMap = data.get("data").get("data");
            } else if (data.has("data")) {
                paraMap = data.get("data");
            }

            if (paraMap == null || !paraMap.isObject()) {
                return result;
            }

            java.util.Iterator<Map.Entry<String, JsonNode>> fields = paraMap.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                try {
                    int paraIndex = Integer.parseInt(entry.getKey());
                    JsonNode value = entry.getValue();
                    int count = 0;
                    if (value.has("count")) {
                        count = value.get("count").asInt(0);
                    }
                    if (count > 0) {
                        result.put(paraIndex, count);
                    }
                } catch (NumberFormatException ignored) {
                    // 跳过非数字 key
                }
            }
        } catch (Exception e) {
            log.warn("解析段评统计响应失败", e);
        }

        return result;
    }

    private String injectCommentIcons(
            String content,
            Map<Integer, Integer> commentCounts,
            String bookId,
            String chapterId,
            String title) {

        String[] paragraphs = content.split("\n", -1);
        StringBuilder enriched = new StringBuilder();
        int paraIndex = 0;
        boolean firstNonEmpty = true;

        for (int i = 0; i < paragraphs.length; i++) {
            String para = paragraphs[i];
            if (para.isEmpty() && i == paragraphs.length - 1) {
                continue;
            }

            String trimmedPara = escapeHtml(para.trim());
            if (trimmedPara.isEmpty()) {
                continue;
            }

            enriched.append("<p>").append(trimmedPara);

            boolean isTitleLine = firstNonEmpty
                    && title != null && !title.isEmpty()
                    && para.trim().equals(title);
            firstNonEmpty = false;

            if (!isTitleLine) {
                Integer count = commentCounts.get(paraIndex);
                if (count != null && count > 0) {
                    String commentUrl = COMMENT_PAGE_PATH
                            + "?bookId=" + encodeParam(bookId)
                            + "&chapterId=" + encodeParam(chapterId)
                            + "&paraIndex=" + paraIndex;

                    String svgDataUri = generateSvgDataUri(count, commentUrl);
                    if (svgDataUri != null) {
                        enriched.append(" <img src='").append(svgDataUri)
                                .append("' style='display:inline-block;vertical-align:middle'/>");
                    }
                }
                paraIndex++;
            }

            enriched.append("</p>\n");
        }

        return enriched.toString().trim();
    }

    private String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private String encodeParam(String s) {
        if (s == null) return "";
        try {
            return java.net.URLEncoder.encode(s, "UTF-8");
        } catch (Exception e) {
            return s;
        }
    }

    private String generateSvgDataUri(int count, String clickUrl) {
        if (count <= 0) return null;

        int width = count > 999 ? 48 : count > 99 ? 39 : 28;
        int height = 20;

        String svg = String.format(
                "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%d\" height=\"%d\" viewBox=\"0 0 %d %d\">" +
                        "<rect x=\"0\" y=\"0\" width=\"%d\" height=\"%d\" rx=\"10\" fill=\"#999\"/>" +
                        "<text x=\"%d\" y=\"14\" text-anchor=\"middle\" fill=\"#fff\" font-size=\"11\" font-weight=\"bold\">%d</text>" +
                        "</svg>",
                width, height, width, height,
                width, height,
                width / 2,
                count
        );

        String base64Svg = Base64.getEncoder().encodeToString(svg.getBytes(StandardCharsets.UTF_8));

        String clickJs = "showCmt('" + escapeJsonStr(clickUrl) + "','番茄',true)";
        String clickMeta = "{\"click\":\"" + escapeJsonStr(clickJs) + "\",\"style\":\"text\"}";

        return "data:image/svg+xml;base64," + base64Svg + "," + clickMeta;
    }

    private String escapeJsonStr(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
