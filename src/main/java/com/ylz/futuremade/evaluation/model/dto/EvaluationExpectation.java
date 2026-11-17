package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 人工预设的评测要点。 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationExpectation {
    /** 标准答案唯一标识。 */
    private String id;
    /** 人工预设的要点描述。 */
    private String description;
    /** 预期负责人，未明确时为空。 */
    private String assignee;
    /** 预期截止时间，未明确时为空。 */
    private String deadline;
    /** 支持该要点的原文发言序号。 */
    private List<Integer> evidence;
}
