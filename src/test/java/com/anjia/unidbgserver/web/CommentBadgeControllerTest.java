package com.anjia.unidbgserver.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CommentBadgeController.class)
class CommentBadgeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void getBadge_count5_returnsPng() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/fqnovel/comment-badge/5"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/png")))
                .andExpect(header().string("Cache-Control", containsString("max-age=86400")))
                .andReturn();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        assertNotNull(image, "Body should decode as a PNG image");
        assertEquals(28, image.getWidth(), "count=5 (1 digit) should render width 28");
        assertEquals(24, image.getHeight(), "Badge height should be 24");
    }

    @Test
    void getBadge_count150_widthDiffers() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/fqnovel/comment-badge/150"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/png")))
                .andReturn();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        assertNotNull(image, "Body should decode as a PNG image");
        assertEquals(33, image.getWidth(), "count=150 (3 digits) should render width 33");
        assertEquals(24, image.getHeight());
    }

    @Test
    void getBadge_withClickMetadataSuffix_tolerated() throws Exception {
        // 浏览器/WebView 请求徽章 URL 时会携带 Legado 点击元数据后缀。
        // 整个后缀按 URL 规则百分号编码（浏览器会对 { } " \ 及非 ASCII 编码），
        // 控制器收到解码后的单一路径段，应能容错解析出前导数字。
        String suffix = ",{\"click\":\"showCmt(\\\"/api/ssr/comment-page?bookId=b1&chapterId=c1&paraIndex=0\\\",\\\"番茄\\\",true)\",\"style\":\"text\"}";
        String encodedSuffix = URLEncoder.encode(suffix, StandardCharsets.UTF_8)
                .replace("+", "%20");

        MvcResult result = mockMvc.perform(get("/api/fqnovel/comment-badge/5" + encodedSuffix))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", startsWith("image/png")))
                .andReturn();

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()));
        assertNotNull(image, "Body with click-meta suffix should still decode as PNG");
        assertEquals(28, image.getWidth());
    }

    @Test
    void getBadge_countZero_returns400() throws Exception {
        mockMvc.perform(get("/api/fqnovel/comment-badge/0"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getBadge_nonNumeric_returns400() throws Exception {
        mockMvc.perform(get("/api/fqnovel/comment-badge/abc"))
                .andExpect(status().isBadRequest());
    }
}