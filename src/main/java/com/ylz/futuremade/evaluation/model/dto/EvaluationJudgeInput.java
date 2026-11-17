package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 评审模型实际收到的样本和待评审输出。 */
@Getter
@Setter
public class EvaluationJudgeInput {
    /** 评测样本及人工预设答案。 */
    private EvaluationCase sample;
    /** 被评审的实际输出。 */
    private String actualOutput;
}
