package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 可供用户确认的进展与来源。
 */
@Getter
@Setter
public class ProgressCandidate {
    /**
     * 校验后的模型判断。
     */
    private ProgressFinding finding;
    /**
     * 由程序匹配的真实来源。
     */
    private KnowledgeSource source;
}
