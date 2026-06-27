package com.anjia.unidbgserver.service;

import com.anjia.unidbgserver.dto.FQCommentListRequest;
import com.anjia.unidbgserver.dto.LegadoCommentRequest;
import com.anjia.unidbgserver.dto.LegadoCommentResponse;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class LegadoCommentService {

    @Resource
    private FQCommentService fqCommentService;

    public CompletableFuture<LegadoCommentResponse> getLegadoComments(LegadoCommentRequest request) {
        FQCommentListRequest commentListRequest = new FQCommentListRequest();
        commentListRequest.setBookId(request.getBookId());
        commentListRequest.setChapterId(request.getChapterId());
        commentListRequest.setParaIndex(request.getParaIndex());
        commentListRequest.setCount(request.getCount());
        commentListRequest.setCursor(request.getCursor());

        return fqCommentService.getCommentList(commentListRequest).thenApply(response -> {
            LegadoCommentResponse legadoCommentResponse = new LegadoCommentResponse();

            if (response == null || response.getCode() == null || response.getCode() != 0 || response.getData() == null) {
                legadoCommentResponse.setComments(Collections.emptyList());
                legadoCommentResponse.setCommentCount(0);
                legadoCommentResponse.setHasMore(false);
                legadoCommentResponse.setNextCursor("");
                return legadoCommentResponse;
            }

            List<String> comments = extractComments(response.getData());
            legadoCommentResponse.setComments(comments);
            legadoCommentResponse.setCommentCount(comments.size());
            legadoCommentResponse.setHasMore(extractHasMore(response.getData()));
            legadoCommentResponse.setNextCursor(extractNextCursor(response.getData()));

            return legadoCommentResponse;
        });
    }

    private List<String> extractComments(JsonNode root) {
        List<String> comments = new ArrayList<>();
        JsonNode items = findFirstArray(root,
            "/data/data_list",
            "/data/comment_list",
            "/data/list",
            "/data/comments",
            "/comment_list",
            "/list");

        if (items == null) {
            return comments;
        }

        for (JsonNode item : items) {
            String text = firstText(item,
                "/comment/common/content/text",
                "/comment_info/text",
                "/comment_info/content",
                "/text",
                "/content",
                "/comment_text",
                "/reply_text");
            if (text != null && !text.trim().isEmpty()) {
                comments.add(text.trim());
            }
        }
        return comments;
    }

    private Boolean extractHasMore(JsonNode root) {
        JsonNode hasMoreNode = findFirst(root,
            "/data/common_list_info/has_more",
            "/data/has_more",
            "/has_more",
            "/data/hasMore",
            "/hasMore");
        if (hasMoreNode == null || hasMoreNode.isMissingNode() || hasMoreNode.isNull()) {
            return false;
        }
        if (hasMoreNode.isBoolean()) {
            return hasMoreNode.asBoolean();
        }
        if (hasMoreNode.isInt() || hasMoreNode.isLong()) {
            return hasMoreNode.asInt() == 1;
        }
        return "1".equals(hasMoreNode.asText()) || "true".equalsIgnoreCase(hasMoreNode.asText());
    }

    private String extractNextCursor(JsonNode root) {
        return firstText(root,
            "/data/common_list_info/cursor",
            "/data/cursor",
            "/cursor",
            "/data/next_cursor",
            "/next_cursor",
            "/data/nextCursor",
            "/nextCursor");
    }

    private JsonNode findFirstArray(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = root.at(pointer);
            if (node != null && node.isArray()) {
                return node;
            }
        }
        return null;
    }

    private JsonNode findFirst(JsonNode root, String... pointers) {
        for (String pointer : pointers) {
            JsonNode node = root.at(pointer);
            if (node != null && !node.isMissingNode() && !node.isNull()) {
                return node;
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode current = node.at(pointer);
            if (current != null && !current.isMissingNode() && !current.isNull()) {
                String value = current.asText();
                if (value != null && !value.trim().isEmpty()) {
                    return value;
                }
            }
        }
        return null;
    }
}
