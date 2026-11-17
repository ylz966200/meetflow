package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 模型评审结果，需由人工复核。 */
@Getter
@Setter
public class EvaluationJudgeResponse {
    /** 模型生成的评审意见。 */
    private String review;
    /** 对应 AI 调用记录编号。 */
    private long callId;
}
