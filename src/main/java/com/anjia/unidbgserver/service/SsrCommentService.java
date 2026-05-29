package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.FQCommentListRequest;
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

        return fqCommentService.getCommentList(request).thenApply(response -> {
            if (response == null || response.getCode() == null || response.getCode() != 0
                    || response.getData() == null) {
                return renderErrorHtml("加载评论失败");
            }
            return renderHtml(response.getData(), bookId, chapterId, paraIndex);
        });
    }

    private String renderErrorHtml(String message) {
        return "<!DOCTYPE html>\n" +
               "<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n" +
               "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no\">\n" +
               "<title>段评</title>\n<style>\n" +
               "*{margin:0;padding:0;box-sizing:border-box}\n" +
               "body{font-family:-apple-system,BlinkMacSystemFont,PingFang SC,Noto Sans SC,sans-serif;background:#f5f5f5;padding:12px;color:#333;line-height:1.6}\n" +
               ".error,.empty{text-align:center;padding:60px 20px;color:#999;font-size:15px}\n" +
               ".error{color:#e74c3c}\n" +
               "@media(prefers-color-scheme:dark){body{background:#1c1c1e;color:#e5e5e5}}\n" +
               "</style>\n</head>\n<body>\n" +
               "<div class=\"error\">" + escapeHtml(message) + "</div>\n" +
               "</body>\n</html>";
    }

    private String renderHtml(JsonNode root, String bookId, String chapterId, Integer paraIndex) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no\">\n")
            .append("<title>段评</title>\n<style>\n")
            .append("*{margin:0;padding:0;box-sizing:border-box}\n")
            .append("body{font-family:-apple-system,BlinkMacSystemFont,PingFang SC,Noto Sans SC,sans-serif;background:#f5f5f5;padding:12px;color:#333;line-height:1.6}\n")
            .append(".comment-card{background:#fff;border-radius:10px;padding:14px 16px;margin-bottom:10px;box-shadow:0 1px 3px rgba(0,0,0,.06)}\n")
            .append(".comment-header{display:flex;align-items:center;gap:10px;margin-bottom:8px}\n")
            .append(".avatar{width:32px;height:32px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:14px;font-weight:600;color:#fff;flex-shrink:0}\n")
            .append(".user-info{flex:1;min-width:0}\n")
            .append(".user-name{font-size:14px;font-weight:500;color:#333}\n")
            .append(".comment-time{font-size:11px;color:#bbb;margin-top:1px}\n")
            .append(".comment-text{font-size:15px;color:#2c2c2c;line-height:1.7;word-break:break-word}\n")
            .append(".comment-footer{display:flex;align-items:center;gap:16px;margin-top:8px;padding-top:8px;border-top:1px solid #f0f0f0}\n")
            .append(".comment-footer .stat{font-size:12px;color:#bbb;display:flex;align-items:center;gap:3px}\n")
            .append(".load-more{display:block;text-align:center;padding:12px 0;margin:16px 0;color:#4a90d9;text-decoration:none;font-size:14px;border-radius:20px;background:#fff;border:1px solid #ddd}\n")
            .append(".empty,.error{text-align:center;padding:60px 20px;color:#999;font-size:15px}\n")
            .append(".error{color:#e74c3c}\n")
            .append("@media(prefers-color-scheme:dark){body{background:#1c1c1e;color:#e5e5e5}.comment-card{background:#2c2c2e;box-shadow:0 1px 3px rgba(0,0,0,.2)}.user-name{color:#e0e0e0}.comment-text{color:#e0e0e0}.comment-footer{border-top-color:#3a3a3c}.comment-footer .stat{color:#666}.load-more{background:#2c2c2e;border-color:#3a3a3c;color:#aaa}}\n")
            .append("</style>\n</head>\n<body>\n");

        JsonNode items = findFirstArray(root,
                "/data/data_list", "/data/comment_list", "/data/list", "/data/comments",
                "/comment_list", "/list");

        if (items == null || !items.isArray() || items.size() == 0) {
            html.append("<div class=\"empty\">此段落暂无评论</div>\n");
        } else {
            for (JsonNode item : items) {
                renderCommentCard(html, item);
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
                .append("\">加载更多</a>\n");
        }

        html.append("</body>\n</html>");
        return html.toString();
    }

    private void renderCommentCard(StringBuilder html, JsonNode item) {
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

        if (text == null || text.trim().isEmpty()) return;

        String displayName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "匿名";
        String avatarLetter = displayName.substring(0, 1).toUpperCase();
        String avatarColor = getAvatarColor(avatarLetter);
        String displayTime = formatTime(createTime);

        html.append("<div class=\"comment-card\">\n")
            .append("<div class=\"comment-header\">\n")
            .append("<div class=\"avatar\" style=\"background:").append(avatarColor).append("\">")
            .append(escapeHtml(avatarLetter)).append("</div>\n")
            .append("<div class=\"user-info\">\n")
            .append("<div class=\"user-name\">").append(escapeHtml(displayName)).append("</div>\n")
            .append("<div class=\"comment-time\">").append(escapeHtml(displayTime)).append("</div>\n")
            .append("</div>\n</div>\n")
            .append("<div class=\"comment-text\">").append(escapeHtml(text.trim())).append("</div>\n")
            .append("<div class=\"comment-footer\">\n")
            .append("<span class=\"stat\">👍 <span class=\"num\">").append(likeCount).append("</span></span>\n")
            .append("<span class=\"stat\">💬 <span class=\"num\">").append(replyCount).append("</span></span>\n")
            .append("</div>\n</div>\n");
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
