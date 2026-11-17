package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 待评审的样本及人工提供的实际输出。 */
@Getter
@Setter
public class EvaluationJudgeRequest {
    /** 内置评测样本编号。 */
    private String caseId;
    /** 待评审的大纲、纪要和行动项文本。 */
    private String actualOutput;
}
