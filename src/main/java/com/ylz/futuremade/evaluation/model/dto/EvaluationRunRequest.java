package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 启动指定会议样本的自动评测。 */
@Getter
@Setter
public class EvaluationRunRequest {
    /** 评测集中的样本编号。 */
    private String caseId;
}
