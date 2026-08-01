package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.service.SsrCommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping(path = "/api/ssr")
public class SsrCommentController {

    @Resource
    private SsrCommentService ssrCommentService;

    @GetMapping(value = "/comment-page", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public CompletableFuture<String> getCommentPage(
            @RequestParam String bookId,
            @RequestParam String chapterId,
            @RequestParam Integer paraIndex,
            @RequestParam(required = false) String cursor) {

        if (bookId == null || bookId.trim().isEmpty()
                || chapterId == null || chapterId.trim().isEmpty()
                || paraIndex == null || paraIndex < 0) {
            throw new IllegalArgumentException("bookId/chapterId/paraIndex 不能为空且 paraIndex 不能为负数");
        }

        return ssrCommentService.renderCommentPage(bookId, chapterId, paraIndex, cursor);
    }

    @GetMapping(value = "/comment-replies", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public CompletableFuture<String> getCommentReplies(
            @RequestParam String commentId,
            @RequestParam(required = false) String bookId,
            @RequestParam(required = false) String chapterId,
            @RequestParam(required = false) String cursor) {

        if (commentId == null || commentId.trim().isEmpty()) {
            return CompletableFuture.completedFuture("<div class=\"reply-error\">评论ID不能为空</div>");
        }

        return ssrCommentService.renderReplyListHtml(commentId, bookId, chapterId, cursor);
    }
}
