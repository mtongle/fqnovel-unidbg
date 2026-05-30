package com.anjia.unidbgserver.web;

import com.anjia.unidbgserver.service.SsrCommentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SsrCommentController.class)
class SsrCommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SsrCommentService ssrCommentService;

    @Test
    void testGetCommentPage_success() throws Exception {
        String html = "<!DOCTYPE html><html><body><div class=\"comment-card\">" +
                "<img class=\"avatar-img\"><div class=\"user-name\">测试用户</div>" +
                "<div class=\"comment-text\">测试评论</div>" +
                "<i class=\"far fa-heart\"></i><i class=\"far fa-comment\"></i></div></body></html>";
        when(ssrCommentService.renderCommentPage(anyString(), anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(html));

        MvcResult result = mockMvc.perform(get("/api/ssr/comment-page")
                        .param("bookId", "1")
                        .param("chapterId", "2")
                        .param("paraIndex", "0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "text/html;charset=UTF-8"))
                .andExpect(content().string(containsString("avatar-img")))
                .andExpect(content().string(containsString("user-name")))
                .andExpect(content().string(containsString("fa-heart")))
                .andExpect(content().string(containsString("测试评论")));
    }

    @Test
    void testGetCommentPage_withCursor() throws Exception {
        String html = "<!DOCTYPE html><html><body><a class=\"load-more\" href=\"?cursor=next\">加载更多</a></body></html>";
        when(ssrCommentService.renderCommentPage(anyString(), anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(html));

        MvcResult result = mockMvc.perform(get("/api/ssr/comment-page")
                        .param("bookId", "1")
                        .param("chapterId", "2")
                        .param("paraIndex", "0")
                        .param("cursor", "next_page_cursor"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("?cursor=next")))
                .andExpect(content().string(containsString("加载更多")));
    }

    @Test
    void testGetCommentPage_missingParams() throws Exception {
        mockMvc.perform(get("/api/ssr/comment-page")
                        .param("bookId", "1")
                        .param("chapterId", "2"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testGetCommentPage_serviceError() throws Exception {
        when(ssrCommentService.renderCommentPage(anyString(), anyString(), anyInt(), any()))
                .thenReturn(CompletableFuture.completedFuture(
                        "<!DOCTYPE html><html><body><div class=\"error\">加载评论失败</div>" +
                        "<script>function toggleTheme(){}</script></body></html>"));

        MvcResult result = mockMvc.perform(get("/api/ssr/comment-page")
                        .param("bookId", "1")
                        .param("chapterId", "2")
                        .param("paraIndex", "0"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<!DOCTYPE html>")))
                .andExpect(content().string(containsString("error")));
    }
}
