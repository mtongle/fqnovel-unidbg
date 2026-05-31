package com.anjia.unidbgserver.dto;

import lombok.Data;

/**
 * 段评回复列表请求 DTO
 * 对应接口: POST /novel/commentapi/reply/list/:comment_id/v1/
 */
@Data
public class FQCommentReplyListRequest {

    /** 评论ID（对应API路径参数 comment_id） */
    private String commentId;

    /** 书籍ID（用于business_param） */
    private String bookId;

    /** 章节ID（对应 body group_id） */
    private String chapterId;

    /** 评论来源，默认502(回复 NovelParaReply) */
    private Integer commentSource = 502;

    /** 分组类型，默认15(Item) */
    private Integer groupType = 15;

    /** 每页数量 */
    private Integer count = 20;

    /** 游标(分页) */
    private String cursor;
}
