package com.ylz.futuremade.evaluation.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 尚需恢复的历史对话来源。 */
@Getter @Setter
public class KnowledgeEvaluationHistorySource {
    /** 历史 AI 调用 ID。 */ private Long aiCallId;
    /** 历史恢复说明。 */ private String note;
}
