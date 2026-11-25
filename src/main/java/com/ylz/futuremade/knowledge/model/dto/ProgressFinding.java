package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 模型提取的待办进展候选，仅描述有来源支持的事项。
 */
@Getter
@Setter
public class ProgressFinding {
    /**
     * 标题，概括本条进展。
     */
    private String title;
    /**
     * 关联类型：1明确相关、2可能相关。
     */
    private Integer relation;
    /**
     * 关联理由。
     */
    private String reason;
    /**
     * 来源纪要条目序号，从零开始。
     */
    private Integer sectionIndex;
    /**
     * 从来源正文逐字引用的证据。
     */
    private String evidence;
    /**
     * 冲突或不确定信息，可为空。
     */
    private String uncertainty;
}
