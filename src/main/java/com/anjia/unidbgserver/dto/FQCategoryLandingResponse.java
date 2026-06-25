package com.anjia.unidbgserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 新分类系统落地页(分类下书籍列表)响应
 * 对应 fqnovel: /reading/bookapi/new_category/landing/v
 * response: {code, message, data: {book_info[], has_more, offset, ...}}
 */
@Data
public class FQCategoryLandingResponse {

    /** 书籍列表 */
    @JsonProperty("book_info")
    private List<FQSearchResponse.BookItem> books;

    /** 分类信息 */
    private Object category;

    /** 分类描述 */
    @JsonProperty("category_desc")
    private Object categoryDesc;

    /** 是否有更多 */
    @JsonProperty("has_more")
    private Boolean hasMore;

    /** 偏移量 */
    private Long offset;

    /** 页面规则 */
    private Object rule;

    /** 搜索选择器 */
    private Object selector;

    /** 会话ID */
    @JsonProperty("session_id")
    private String sessionId;

    /** 页面样式 */
    private Object style;
}
