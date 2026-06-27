package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.dto.FQCommentIdeaRequest;
import com.anjia.unidbgserver.dto.FQCommentListRequest;
import com.anjia.unidbgserver.dto.FQCommentReplyListRequest;
import com.anjia.unidbgserver.dto.FQNovelResponse;
import com.anjia.unidbgserver.service.FQCommentService;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping(path = "/api/fqcomment", produces = MediaType.APPLICATION_JSON_VALUE)
public class FQCommentController {

    @Autowired
    private FQCommentService fqCommentService;

    @PostMapping("/reply/list")
    public CompletableFuture<FQNovelResponse<JsonNode>> getReplyList(
            @RequestBody FQCommentReplyListRequest request) {

        log.debug("段评回复列表请求 - commentId: {}, chapterId: {}",
                request.getCommentId(), request.getChapterId());

        if (request.getCommentId() == null || request.getCommentId().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                FQNovelResponse.error("评论ID不能为空"));
        }

        return fqCommentService.getReplyList(request);
    }

    @PostMapping("/idea")
    public CompletableFuture<FQNovelResponse<JsonNode>> getCommentIdeaList(
            @RequestBody FQCommentIdeaRequest request) {

        log.debug("段评统计请求 - chapterId: {}", request.getChapterId());

        if (request.getChapterId() == null || request.getChapterId().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                FQNovelResponse.error("章节ID不能为空"));
        }

        return fqCommentService.getCommentIdeaList(request);
    }

    @PostMapping("/list")
    public CompletableFuture<FQNovelResponse<JsonNode>> getCommentList(
            @RequestBody FQCommentListRequest request) {

        log.debug("段评详情请求 - chapterId: {}, paraIndex: {}",
                request.getChapterId(), request.getParaIndex());

        if (request.getChapterId() == null || request.getChapterId().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                FQNovelResponse.error("章节ID不能为空"));
        }

        if (request.getBookId() == null || request.getBookId().trim().isEmpty()) {
            return CompletableFuture.completedFuture(
                FQNovelResponse.error("书籍ID不能为空"));
        }

        return fqCommentService.getCommentList(request);
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> healthStatus = new HashMap<>();
        healthStatus.put("status", "UP");
        healthStatus.put("service", "FQComment Service");
        healthStatus.put("timestamp", System.currentTimeMillis());
        return healthStatus;
    }
}
