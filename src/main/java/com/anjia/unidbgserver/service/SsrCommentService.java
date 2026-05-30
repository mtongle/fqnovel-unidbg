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
               "<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css\">\n" +
               "<title>段评</title>\n<style>\n" +
               "*{margin:0;padding:0;box-sizing:border-box}\n" +
               "body{font-family:-apple-system,BlinkMacSystemFont,PingFang SC,Noto Sans SC,sans-serif;background:#f5f5f5;padding:12px;color:#333;line-height:1.6}\n" +
               ".empty,.error{text-align:center;padding:80px 20px;color:#999;font-size:15px}\n" +
               ".error{color:#e74c3c}\n" +
               "@media(prefers-color-scheme:dark){body{background:#1a1a1a;color:#e0e0e0}}\n" +
               "</style>\n</head>\n<body>\n" +
               "<button class=\"theme-toggle\" onclick=\"toggleTheme()\" title=\"切换主题\"><i class=\"fas fa-moon\"></i></button>\n" +
               "<div class=\"error\">" + escapeHtml(message) + "</div>\n" +
               "<script>\n" +
               "(function(){var t=localStorage.getItem('theme'),d=window.matchMedia('(prefers-color-scheme:dark)').matches;if(t==='dark'||(!t&&d))document.body.classList.add('dark')})();\n" +
               "function toggleTheme(){document.body.classList.toggle('dark');localStorage.setItem('theme',document.body.classList.contains('dark')?'dark':'light')}\n" +
               "</script>\n" +
               "</body>\n</html>";
    }

    private String renderHtml(JsonNode root, String bookId, String chapterId, Integer paraIndex) {
        StringBuilder html = new StringBuilder();
        html.append("<!DOCTYPE html>\n")
            .append("<html lang=\"zh-CN\">\n<head>\n<meta charset=\"UTF-8\">\n")
            .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0,maximum-scale=1.0,user-scalable=no\">\n")
            .append("<link rel=\"stylesheet\" href=\"https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css\">\n")
            .append("<title>段评</title>\n<style>\n")
            .append("*{margin:0;padding:0;box-sizing:border-box}\n")
            .append("body{font-family:-apple-system,BlinkMacSystemFont,PingFang SC,Noto Sans SC,sans-serif;background:#f5f5f5;padding:12px;color:#333;line-height:1.6}\n")
            .append(".comment-card{background:#fff;border-radius:12px;padding:16px;margin-bottom:12px;box-shadow:0 1px 4px rgba(0,0,0,.08)}\n")
            .append(".comment-header{display:flex;align-items:flex-start;gap:12px;margin-bottom:10px}\n")
            .append(".avatar{width:40px;height:40px;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:16px;font-weight:600;color:#fff;flex-shrink:0;overflow:hidden}\n")
            .append(".avatar-img{width:100%;height:100%;object-fit:cover;border-radius:50%}\n")
            .append(".user-info{flex:1;min-width:0}\n")
            .append(".user-name-row{display:flex;align-items:center;gap:6px;flex-wrap:wrap}\n")
            .append(".user-name{font-size:14px;font-weight:600;color:#333}\n")
            .append(".tag-author{display:inline-block;font-size:11px;background:#e8f5e9;color:#2e7d32;padding:1px 6px;border-radius:3px;font-weight:500}\n")
            .append(".tag-sticker{display:inline-block;font-size:10px;background:#fff3e0;color:#e65100;padding:1px 5px;border-radius:3px}\n")
            .append(".comment-time{font-size:11px;color:#bbb;margin-top:2px}\n")
            .append(".comment-text{font-size:15px;color:#222;line-height:1.7;word-break:break-word;margin-bottom:6px}\n")
            .append(".comment-text.collapsed{overflow:hidden;display:-webkit-box;-webkit-line-clamp:4;-webkit-box-orient:vertical}\n")
            .append(".expand-btn{font-size:13px;color:#4a90d9;cursor:pointer;display:inline-block;margin-bottom:4px}\n")
            .append(".comment-footer{display:flex;align-items:center;gap:16px;margin-top:8px;padding-top:10px;border-top:1px solid #f0f0f0}\n")
            .append(".comment-footer .stat{font-size:13px;color:#999;display:flex;align-items:center;gap:4px}\n")
            .append(".comment-footer .stat i{font-size:14px}\n")
            .append(".load-more{display:block;text-align:center;padding:14px;margin:20px 0;color:#4a90d9;text-decoration:none;font-size:14px;border-radius:24px;background:#fff;border:1px solid #ddd;font-weight:500}\n")
            .append(".theme-toggle{position:fixed;top:12px;right:12px;z-index:100;width:36px;height:36px;border-radius:50%;border:none;background:#fff;box-shadow:0 1px 4px rgba(0,0,0,.15);cursor:pointer;display:flex;align-items:center;justify-content:center;font-size:16px;color:#666}\n")
            .append(".empty,.error{text-align:center;padding:80px 20px;color:#999;font-size:15px}\n")
            .append(".empty i{font-size:48px;margin-bottom:16px;opacity:.3}\n")
            .append(".error{color:#e74c3c}\n")
            .append("@media(prefers-color-scheme:dark){body{background:#1a1a1a;color:#e0e0e0}.comment-card{background:#2d2d2d;box-shadow:0 1px 4px rgba(0,0,0,.3)}.user-name{color:#e0e0e0}.comment-text{color:#ccc}.comment-footer{border-top-color:#3d3d3d}.comment-footer .stat{color:#777}.load-more{background:#2d2d2d;border-color:#3d3d3d;color:#5b9bd5}.tag-author{background:#1b4332;color:#4caf50}.tag-sticker{background:#3e2723;color:#ff9800}}\n")
            .append("@media(min-width:768px){body{padding:20px;max-width:700px;margin:0 auto}.comment-card{padding:20px 24px;border-radius:14px}}\n")
            .append("</style>\n</head>\n<body>\n")
            .append("<button class=\"theme-toggle\" onclick=\"toggleTheme()\" title=\"切换主题\"><i class=\"fas fa-moon\"></i></button>\n");

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

        html.append("<script>\n")
            .append("(function(){var t=localStorage.getItem('theme'),d=window.matchMedia('(prefers-color-scheme:dark)').matches;if(t==='dark'||(!t&&d))document.body.classList.add('dark')})();\n")
            .append("function toggleTheme(){document.body.classList.toggle('dark');localStorage.setItem('theme',document.body.classList.contains('dark')?'dark':'light')}\n")
            .append("function toggleExpand(b){var t=b.previousElementSibling;t.classList.toggle('collapsed');b.textContent=t.classList.contains('collapsed')?'展开全文':'收起'}\n")
            .append("</script>\n")
            .append("</body>\n</html>");
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
        String rawAvatarUrl = firstText(common, "/user_info/base_info/user_avatar", "/user_info/user_avatar", "/avatar");
        String avatarUrl = rawAvatarUrl != null ? rawAvatarUrl.replace("http://", "https://") : null;
        String stickerName = firstText(common, "/user_info/user_tag/sticker/sticker/name");
        boolean isAuthor = firstBool(common, "/user_info/user_tag/is_author", "/is_author");

        if (text == null || text.trim().isEmpty()) return;

        String displayName = (userName != null && !userName.trim().isEmpty()) ? userName.trim() : "匿名";
        String avatarLetter = displayName.substring(0, 1).toUpperCase();
        String avatarColor = getAvatarColor(avatarLetter);
        String displayTime = formatTime(createTime);

        boolean needsExpand = text != null && text.trim().length() > 150;

        html.append("<div class=\"comment-card\">\n")
            .append("<div class=\"comment-header\">\n")
            .append("<div class=\"avatar\">\n");

        // Avatar: use real URL if available, with onerror fallback to letter
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            html.append("<img class=\"avatar-img\" src=\"").append(escapeHtml(avatarUrl))
                .append("\" onerror=\"this.style.display='none'\" alt=\"\" />\n");
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
            .append("\">").append(convertEmoji(escapeHtml(text.trim()))).append("</div>\n");

        if (needsExpand) {
            html.append("<span class=\"expand-btn\" onclick=\"toggleExpand(this)\">展开全文</span>\n");
        }

        html.append("<div class=\"comment-footer\">\n")
            .append("<span class=\"stat\"><i class=\"far fa-heart\"></i> <span>").append(likeCount).append("</span></span>\n")
            .append("<span class=\"stat\"><i class=\"far fa-comment\"></i> <span>").append(replyCount).append("</span>")
            .append(replyCount > 0 ? "条回复" : "").append("</span>\n")
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
