package com.anjia.unidbgserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FQNovel 注册密钥载荷
 * 对应 Rust 中的 FQRegisterKeyPayload 结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FQRegisterKeyPayload {

    /**
     * 密钥内容 (加密后的)
     */
    @JsonProperty("content")
    private String content;

    /**
     * 密钥版本
     */
    @JsonProperty("keyver")
    private long keyver;
}
