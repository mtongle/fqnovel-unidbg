package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.FQCommentListRequest;
import com.anjia.unidbgserver.dto.FQCommentReplyListRequest;
import com.anjia.unidbgserver.utils.CommonUtils;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class SsrCommentService {

    @Resource
    private FQCommentService fqCommentService;

    public CompletableFuture<String> renderCommentPage(String bookId, String chapterId,
                                                        Integer paraIndex, String cursor) {
        FQCommentListRequest request = new FQCommentListRequest();
        request.setBookId(bookId);
        request.setChapterId(chapterId);
        request.setParaIndex(paraIndex);
        request.setCursor(cursor);
        request.setCount(20);

        return fqCommentService.getCommentList(request)
                .thenApply(response -> {
                    if (response == null || response.getCode() == null || response.getCode() != 0
                            || response.getData() == null) {
                        return renderErrorHtml("加载评论失败");
                    }
                    return renderHtml(response.getData(), bookId, chapterId, paraIndex);
                })
                .exceptionally(e -> {
                    log.error("渲染段评页面异常 - bookId: {}, chapterId: {}", bookId, chapterId, e);
                    return renderErrorHtml("页面渲染异常: " + (e.getMessage() != null ? e.getMessage() : "未知错误"));
                });
    }

    public CompletableFuture<String> renderReplyListHtml(String commentId, String bookId, String chapterId, String cursor) {
        FQCommentReplyListRequest request = new FQCommentReplyListRequest();
        request.setCommentId(commentId);
        request.setBookId(bookId);
        request.setChapterId(chapterId);
        request.setCount(5);
        if (cursor != null && !cursor.trim().isEmpty()) {
            request.setCursor(cursor);
        }

        String finalBookId = bookId;
        String finalChapterId = chapterId;
        return fqCommentService.getReplyList(request)
                .thenApply(response -> {
                    if (response == null || response.getCode() == null || response.getCode() != 0
                            || response.getData() == null) {
                        log.warn("段评回复加载失败 - commentId: {}, code: {}, message: {}",
                                commentId,
                                response != null ? response.getCode() : null,
                                response != null ? response.getMessage() : "null response");
                        return "<div class=\"reply-error\">加载回复失败</div>";
                    }
                    String html = renderReplyHtml(response.getData(), commentId, finalBookId, finalChapterId);
                    log.debug("段评回复渲染完成 - commentId: {}, html长度: {}", commentId, html.length());
                    return html;
                })
                .exceptionally(e -> {
                    log.error("段评回复渲染异常 - commentId: {}", commentId, e);
                    return "<div class=\"reply-error\">加载回复异常: " + escapeHtml(e.getMessage()) + "</div>";
                });
    }

    private String renderReplyHtml(JsonNode root, String commentId, String bookId, String chapterId) {
        JsonNode replyList = findFirstArray(root,
                "/data/reply_list", "/reply_list");
        if (replyList == null || !replyList.isArray() || replyList.size() == 0) {
            return "<div class=\"reply-empty\">暂无回复</div>";
        }

        StringBuilder html = new StringBuilder();
        for (JsonNode reply : replyList) {
            renderReplyCard(html, reply);
        }

        JsonNode clInfo = root.at("/data/comment_list_info");
        if (!clInfo.isMissingNode()) {
            boolean hasMore = clInfo.has("has_more") ? clInfo.get("has_more").asBoolean(false) : false;
            String cursor = clInfo.has("cursor") ? clInfo.get("cursor").asText("") : "";
            if (hasMore && cursor != null && !cursor.isEmpty()) {
                html.append("<button class=\"reply-load-more\" onclick=\"loadMoreReplies(this)\"")
                    .append(" data-comment-id=\"").append(escapeHtml(commentId))
                    .append("\" data-book-id=\"").append(escapeHtml(bookId != null ? bookId : ""))
                    .append("\" data-chapter-id=\"").append(escapeHtml(chapterId != null ? chapterId : ""))
                    .append("\" data-cursor=\"").append(escapeHtml(cursor))
                    .append("\">✦ 加载更多回复</button>\n");
            }
        }

        return html.toString();
    }

    private void renderReplyCard(StringBuilder html, JsonNode reply) {
        JsonNode common = reply.has("Common") ? reply.get("Common") : reply.has("common") ? reply.get("common") : reply;
        if (common == null || !common.has("content")) return;

        String text = firstText(common, "/content/text", "/text", "/content");
        String userName = firstText(common, "/user_info/base_info/user_name", "/user_info/user_name", "/user_name");
        long createTime = firstLong(common, "/create_timestamp", "/create_time", "/time");
        int diggCount = firstInt(reply, "/stat/digg_count", "/stat/like_count");
        String rawAvatarUrl = firstText(common, "/user_info/base_info/user_avatar", "/user_info/user_avatar", "/avatar");
        String avatarUrl = rawAvatarUrl;
        if (avatarUrl != null && avatarUrl.startsWith("http://")) {
            avatarUrl = "https://" + avatarUrl.substring(7);
        }
        String replyID = reply.has("reply_id") ? reply.get("reply_id").asText("") : "";

        // reply_to_user_info is at reply level (not inside common) in actual API response
        String replyToUserName = firstText(reply, "/reply_to_user_info/base_info/user_name", "/reply_to_user_info/user_name");
        if (replyToUserName == null || replyToUserName.isEmpty()) {
            replyToUserName = firstText(common, "/reply_to_user_info/base_info/user_name", "/reply_to_user_info/user_name");
        }

        // Check for inline images in reply content
        JsonNode contentImages = common.at("/content/image_data_list/image_data");
        boolean hasImages = contentImages != null && contentImages.isArray() && contentImages.size() > 0;

        if ((text == null || text.trim().isEmpty()) && !hasImages) return;

        String displayName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "匿名";
        String avatarLetter = displayName.substring(0, 1).toUpperCase();
        String avatarColor = getAvatarColor(avatarLetter);
        String displayTime = formatTime(createTime);

        html.append("<div class=\"reply-card\" data-reply-id=\"").append(escapeHtml(replyID)).append("\">\n")
                .append("<div class=\"reply-header\">\n")
                .append("<div class=\"reply-avatar\">\n");

        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            html.append("<img class=\"reply-avatar-img\" src=\"").append(escapeHtml(avatarUrl))
                    .append("\" onerror=\"fixHeicImg(this)\" alt=\"\" />\n");
        }
        html.append("<span style=\"background:").append(avatarColor).append("\">")
                .append(escapeHtml(avatarLetter)).append("</span>\n")
                .append("</div>\n")
                .append("<span class=\"reply-user-name\">").append(escapeHtml(displayName)).append("</span>\n");

        if (replyToUserName != null && !replyToUserName.isEmpty()) {
            html.append("<span class=\"reply-to\">回复 <span class=\"at-user\">@")
                    .append(escapeHtml(replyToUserName)).append("</span></span>\n");
        }

        html.append("<span class=\"reply-time\">").append(escapeHtml(displayTime)).append("</span>\n")
                .append("</div>\n")
                .append("<div class=\"reply-text\">").append(convertEmoji(escapeHtml(text != null ? text.trim() : ""))).append("</div>\n");

        // Reply inline images
        if (hasImages) {
            renderCommentImages(html, contentImages);
        }

        // Sub-replies
        JsonNode subReplyList = reply.has("sub_reply") ? reply.get("sub_reply") : null;
        if (subReplyList != null && subReplyList.isArray() && subReplyList.size() > 0) {
            html.append("<div class=\"sub-reply\">\n");
            int subCount = subReplyList.size();
            int showCount = Math.min(subCount, 3);
            for (int i = 0; i < showCount; i++) {
                JsonNode sr = subReplyList.get(i);
                JsonNode srCommon = sr.has("common") ? sr.get("common") : sr;
                String srText = firstText(srCommon, "/content/text", "/text");
                String srUser = firstText(srCommon, "/user_info/base_info/user_name", "/user_info/user_name");
                String srReplyTo = firstText(sr, "/reply_to_user_info/base_info/user_name", "/reply_to_user_info/user_name");
                if (srText != null && !srText.trim().isEmpty()) {
                    html.append("<div class=\"sub-reply-card\">")
                            .append(escapeHtml(srUser != null ? srUser : "匿名"))
                            .append(srReplyTo != null && !srReplyTo.isEmpty() ? " 回复 @" + escapeHtml(srReplyTo) : "")
                            .append(": ").append(convertEmoji(escapeHtml(srText.trim())))
                            .append("</div>\n");
                }
            }
            if (subCount > 3) {
                html.append("<span class=\"sub-reply-more\">查看全部").append(subCount).append("条子回复</span>\n");
            }
            html.append("</div>\n");
        }

        html.append("<div class=\"reply-footer\">\n")
                .append("<span class=\"stat\">✦ ").append(diggCount).append("</span>\n")
                .append("</div>\n")
                .append("</div>\n");
    }

    private String renderErrorHtml(String message) {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n" +
               "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no\">\n" +
               "<link rel=\"stylesheet\" href=\"/css/comment-page.css?v=0.0.4\">\n" +
               "<title>✦ 段评</title>\n</head>\n<body>\n" +
               "<button class=\"theme-toggle\" onclick=\"toggleTheme()\" title=\"切换主题\">✦</button>\n" +
               "<div class=\"error\">✦ " + escapeHtml(message) + "</div>\n" +
               "<script src=\"/js/comment-page.js?v=0.0.4\"></script>\n" +
               "</body>\n</html>";
    }

    private String renderHtml(JsonNode root, String bookId, String chapterId, Integer paraIndex) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no\">\n")
            .append("<link rel=\"stylesheet\" href=\"/css/comment-page.css?v=0.0.4\">\n")
            .append("<title>✦ 段评</title>\n</head>\n<body>\n")
            .append("<button class=\"theme-toggle\" onclick=\"toggleTheme()\" title=\"切换主题\">✦</button>\n");

        JsonNode items = findFirstArray(root,
                "/data/data_list", "/data/comment_list", "/data/list", "/data/comments",
                "/comment_list", "/list");

        if (items == null || !items.isArray() || items.size() == 0) {
            html.append("<div class=\"empty\">此段落暂无评论</div>\n");
        } else {
            for (JsonNode item : items) {
                renderCommentCard(html, item, bookId, chapterId);
            }
        }

        boolean hasMore = firstBool(root, "/data/common_list_info/has_more", "/data/has_more", "/has_more");
        String nextCursor = firstText(root, "/data/common_list_info/cursor", "/data/cursor", "/cursor");
        if (hasMore && nextCursor != null && !nextCursor.isEmpty()) {
            html.append("<a class=\"load-more\" href=\"?bookId=")
                .append(escapeHtml(bookId))
                .append("&chapterId=").append(escapeHtml(chapterId))
                .append("&paraIndex=").append(paraIndex)
                .append("&cursor=").append(escapeHtml(nextCursor))
                .append("\">✦ 加载更多</a>\n");
        }

        html.append("<script src=\"/js/comment-page.js?v=0.0.4\"></script>\n")
            .append("</body>\n</html>");
        return html.toString();
    }

    private void renderCommentCard(StringBuilder html, JsonNode item, String bookId, String chapterId) {
        JsonNode common = getCommentNode(item);
        if (common == null) return;

        JsonNode stat = getStatNode(item);

        String text = firstText(common, "/content/text", "/text", "/content", "/comment_text");
        String userName = firstText(common, "/user_info/base_info/user_name", "/user_info/user_name", "/user_name", "/user/nickname", "/nickname");
        long createTime = firstLong(common, "/create_timestamp", "/create_time", "/time");
        int likeCount = stat != null ? firstInt(stat, "/digg_count", "/like_count") :
                         firstInt(common, "/digg_count", "/like_count");
        int replyCount = stat != null ? firstInt(stat, "/reply_count") :
                          firstInt(common, "/reply_count");
        String rawAvatarUrl = firstText(common, "/user_info/base_info/user_avatar", "/user_info/user_avatar", "/avatar");
        String avatarUrl = rawAvatarUrl;
        if (avatarUrl != null && avatarUrl.startsWith("http://")) {
            avatarUrl = "https://" + avatarUrl.substring(7);
        }
        String stickerName = firstText(common, "/user_info/user_tag/sticker/sticker/name");
        boolean isAuthor = firstBool(common, "/user_info/user_tag/is_author", "/is_author");

        // Check for inline images in comment content
        JsonNode contentImages = common.at("/content/image_data_list/image_data");
        boolean hasImages = contentImages != null && contentImages.isArray() && contentImages.size() > 0;

        if ((text == null || text.trim().isEmpty()) && !hasImages) return;

        String displayName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "匿名";
        String avatarLetter = displayName.substring(0, 1).toUpperCase();
        String avatarColor = getAvatarColor(avatarLetter);
        String displayTime = formatTime(createTime);

        boolean needsExpand = text != null && text.trim().length() > 150;

        // Get comment_id from different possible locations
        String commentId = item.has("comment_id") ? item.get("comment_id").asText("") : "";
        if (commentId.isEmpty() && item.has("comment")) {
            commentId = item.get("comment").has("comment_id") ? item.get("comment").get("comment_id").asText("") : "";
        }
        if (commentId.isEmpty() && common != null) {
            commentId = common.has("comment_id") ? common.get("comment_id").asText("") : "";
        }

        html.append("<div class=\"comment-card\" data-comment-id=\"").append(escapeHtml(commentId)).append("\">\n")
            .append("<div class=\"comment-header\">\n")
            .append("<div class=\"avatar\">\n");

        // Avatar: real URL, proactively converted via heic2any on page load
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            html.append("<img class=\"avatar-img\" src=\"").append(escapeHtml(avatarUrl))
                .append("\" alt=\"\" />\n");
        }
        html.append("<div class=\"avatar-letter\" style=\"background:").append(avatarColor).append("\">")
            .append(escapeHtml(avatarLetter)).append("</div>\n")
            .append("</div>\n")
            .append("<div class=\"user-info\">\n")
            .append("<div class=\"user-name-row\">\n")
            .append("<span class=\"user-name\">").append(escapeHtml(displayName)).append("</span>\n");

        // Author badge
        if (isAuthor) {
            html.append("<span class=\"tag-author\">作者</span>\n");
        }
        // Sticker badge
        if (stickerName != null && !stickerName.isEmpty()) {
            html.append("<span class=\"tag-sticker\">").append(escapeHtml(stickerName)).append("</span>\n");
        }

        html.append("</div>\n")
            .append("<div class=\"comment-time\">").append(escapeHtml(displayTime)).append("</div>\n")
            .append("</div>\n</div>\n")
            .append("<div class=\"comment-text")
            .append(needsExpand ? " collapsed" : "")
            .append("\">").append(convertEmoji(escapeHtml(text != null ? text.trim() : ""))).append("</div>\n");

        if (needsExpand) {
            html.append("<span class=\"expand-btn\" onclick=\"toggleExpand(this)\">✦ 展开全文</span>\n");
        }

        // Comment inline images
        if (hasImages) {
            renderCommentImages(html, contentImages);
        }

        html.append("<div class=\"comment-footer\">\n")
            .append("<span class=\"stat\">✦ <span>").append(likeCount).append("</span></span>\n")
            .append("<span class=\"stat\">✦ <span>").append(replyCount).append("</span>")
            .append(replyCount > 0 ? "条回复" : "").append("</span>\n")
            .append("</div>\n");

        if (replyCount > 0) {
            html.append("<div class=\"reply-section\" data-comment-id=\"")
                .append(escapeHtml(commentId))
                .append("\" data-book-id=\"")
                .append(escapeHtml(bookId != null ? bookId : ""))
                .append("\" data-chapter-id=\"")
                .append(escapeHtml(chapterId != null ? chapterId : ""))
                .append("\">\n")
                .append("<button class=\"reply-toggle\" onclick=\"loadReplies(this)\">\n")
                .append("✦ 查看").append(replyCount).append("条回复\n")
                .append("</button>\n")
                .append("<div class=\"reply-list\"></div>\n")
                .append("</div>\n");
        }

        html.append("</div>\n");
    }

    private static JsonNode getCommentNode(JsonNode item) {
        if (item == null) return null;
        JsonNode comment = item.has("comment") ? item.get("comment") : item;
        return comment.has("common") ? comment.get("common") : comment;
    }

    private static JsonNode getStatNode(JsonNode item) {
        if (item == null) return null;
        JsonNode comment = item.has("comment") ? item.get("comment") : item;
        return comment.has("stat") ? comment.get("stat") : null;
    }

    /**
     * 渲染评论/回复中的图片（image_data_list.image_data）
     * 图片来自 FQ API 的 content.image_data_list.image_data[]
     */
    private void renderCommentImages(StringBuilder html, JsonNode images) {
        if (images == null || !images.isArray() || images.size() == 0) return;

        html.append("<div class=\"comment-images\">\n");
        for (JsonNode img : images) {
            String url = firstText(img, "/web_url", "/expand_web_url", "/web_uri");
            if (url == null || url.isEmpty()) continue;

            // 统一使用 HTTPS
            if (url.startsWith("http://")) {
                url = "https://" + url.substring(7);
            }

            html.append("<img class=\"comment-image\" src=\"")
                .append(escapeHtml(url))
                .append("\" loading=\"lazy\" alt=\"\"")
                .append(" onerror=\"fixHeicImg(this)\" />\n");
        }
        html.append("</div>\n");
    }

    private static JsonNode findFirstArray(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = root.at(pointer);
            if (node != null && node.isArray()) return node;
        }
        return null;
    }

    private static String firstText(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode current = node.at(pointer);
            if (current != null && !current.isMissingNode() && !current.isNull()) {
                String value = current.asText();
                if (value != null && !value.trim().isEmpty()) return value;
            }
        }
        return null;
    }

    private static int firstInt(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode current = node.at(pointer);
            if (current != null && !current.isMissingNode() && !current.isNull()) {
                return current.asInt(0);
            }
        }
        return 0;
    }

    private static long firstLong(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode current = node.at(pointer);
            if (current != null && !current.isMissingNode() && !current.isNull()) {
                return current.asLong(0);
            }
        }
        return 0;
    }

    private static boolean firstBool(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode current = node.at(pointer);
            if (current != null && !current.isMissingNode() && !current.isNull()) {
                return current.asBoolean(false);
            }
        }
        return false;
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String convertEmoji(String text) {
        if (text == null || text.isEmpty()) return "";
        String[][] emojiMap = {
            {"[微笑]", "\uD83D\uDE0A"}, {"[偷笑]", "\uD83D\uDE48"}, {"[笑哭]", "\uD83D\uDE02"},
            {"[害羞]", "\uD83D\uDE0A"}, {"[爱慕]", "\uD83D\uDE0D"}, {"[飞吻]", "\uD83D\uDE18"},
            {"[奸笑]", "\uD83D\uDE0F"}, {"[尬笑]", "\uD83D\uDE05"}, {"[思考]", "\uD83E\uDD14"},
            {"[撇嘴]", "\uD83D\uDE15"}, {"[酷]", "\uD83D\uDE0E"}, {"[翻白眼]", "\uD83D\uDE44"},
            {"[惊呆]", "\uD83D\uDE2E"}, {"[震惊]", "\uD83D\uDE31"}, {"[送心]", "\u2764\uFE0F"},
            {"[委屈]", "\uD83D\uDE1E"}, {"[快哭了]", "\uD83D\uDE22"}, {"[哭]", "\uD83D\uDE2D"},
            {"[大笑]", "\uD83D\uDE04"}, {"[舔屏]", "\uD83D\uDE0B"}, {"[怒]", "\uD83D\uDE20"},
            {"[捂脸]", "\uD83E\uDD26"}, {"[恐惧]", "\uD83D\uDE28"}, {"[抓狂]", "\uD83D\uDE2B"},
            {"[赞]", "\uD83D\uDC4D"}, {"[爱心]", "\u2764\uFE0F"}, {"[吃瓜]", "\uD83C\uDF49"},
            {"[你细品]", "\uD83E\uDD14"}, {"[OK]", "\uD83C\uDD97"}, {"[石化]", "\uD83D\uDE33"},
            {"[敬礼]", "\uD83D\uDE4B"}, {"[尬]", "\uD83D\uDE05"}, {"[狗头]", "\uD83D\uDC15"},
        };
        String result = text;
        for (String[] pair : emojiMap) {
            result = result.replace(pair[0], pair[1]);
        }
        return result;
    }

    private static String getAvatarColor(String letter) {
        int hash = Math.abs(letter.hashCode());
        String[] colors = {"#e74c3c","#3498db","#2ecc71","#f39c12","#9b59b6",
                "#1abc9c","#e67e22","#2980b9","#27ae60","#8e44ad",
                "#16a085","#d35400","#c0392b","#7f8c8d","#2c3e50"};
        return colors[hash % colors.length];
    }

    private static String formatTime(long ts) {
        if (ts <= 0) return "";
        if (ts > 1e12) ts /= 1000;
        long now = System.currentTimeMillis() / 1000;
        long diff = now - ts;
        if (diff < 60) return "刚刚";
        if (diff < 3600) return (diff / 60) + "分钟前";
        if (diff < 86400) return (diff / 3600) + "小时前";
        if (diff < 2592000) return (diff / 86400) + "天前";
        return (diff / 2592000) + "个月前";
    }
}
