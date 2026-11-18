package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 对外展示的纪要索引任务状态。
 */
@Getter
@Setter
public class KnowledgeIndexStatusView {
    /**
     * 索引关联的纪要版本。
     */
    private Integer minutesVersion;
    /**
     * 索引任务状态编码。
     */
    private Integer status;
    /**
     * 不包含敏感内容的失败类型。
     */
    private String errorCode;
}
