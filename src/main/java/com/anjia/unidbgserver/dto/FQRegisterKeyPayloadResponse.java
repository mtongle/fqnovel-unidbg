package com.anjia.unidbgserver.dto;

import com.anjia.unidbgserver.service.FQCrypto;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * FQNovel 注册密钥载荷响应
 * 对应 Rust 中的 FQRegisterKeyPayload 结构
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FQRegisterKeyPayloadResponse {

    /**
     * 密钥内容 (加密后的)
     */
    @JsonProperty("key")
    private String key;

    /**
     * 密钥版本
     */
    @JsonProperty("keyver")
    private long keyver;

    /**
     * 获取解密后的真实密钥 (前16字节)
     * 使用新的解密算法，遵循 Python 中的 decrypt_registerkey 模式
     *
     * @return 十六进制密钥字符串 (前16字节)，解密失败返回 null
     */
    @JsonIgnore
    public String getKey() {
        try {
            return FQCrypto.getRealKey(this.key);
        } catch (Exception e) {
            return null;
        }
    }
}
