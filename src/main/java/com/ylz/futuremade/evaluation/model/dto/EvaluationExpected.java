package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 评测样本的标准答案。 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationExpected {
    /** 期望大纲要点。 */
    private List<EvaluationExpectation> outline;
    /** 期望纪要要点。 */
    private List<EvaluationExpectation> minutes;
    /** 期望行动项。 */
    private List<EvaluationExpectation> actions;
}
