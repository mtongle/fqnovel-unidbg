package com.anjia.unidbgserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 新分类系统Cell数据响应
 * 对应 fqnovel: /reading/bookapi/new_category/cell/v
 * response: {code, message, data: {cells[], has_more, ...}}
 */
@Data
public class FQCategoryCellResponse {

    /** Cell数据列表(原始JSON，结构随tab类型变化) */
    private List<Object> cells;

    /** 是否有更多 */
    @JsonProperty("has_more")
    private Boolean hasMore;

    /** 请求时间戳 */
    @JsonProperty("req_timestamp")
    private Long reqTimestamp;

    /** 请求类型 */
    @JsonProperty("req_type")
    private Integer reqType;
}
