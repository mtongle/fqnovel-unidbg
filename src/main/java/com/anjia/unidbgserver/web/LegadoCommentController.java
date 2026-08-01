package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.dto.LegadoCommentRequest;
import com.anjia.unidbgserver.dto.LegadoCommentResponse;
import com.anjia.unidbgserver.service.LegadoCommentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RestController
@RequestMapping(path = "/api/legado", produces = MediaType.APPLICATION_JSON_VALUE)
public class LegadoCommentController {

    @Resource
    private LegadoCommentService legadoCommentService;

    @PostMapping("/comment")
    public CompletableFuture<LegadoCommentResponse> getComment(
        @RequestBody LegadoCommentRequest request) {

        if (request.getBookId() == null || request.getBookId().trim().isEmpty()) {
            throw new IllegalArgumentException("书籍ID不能为空");
        }
        if (request.getChapterId() == null || request.getChapterId().trim().isEmpty()) {
            throw new IllegalArgumentException("章节ID不能为空");
        }
        if (request.getParaIndex() == null) {
            throw new IllegalArgumentException("段落索引不能为空");
        }

        return legadoCommentService.getLegadoComments(request);
    }
}
