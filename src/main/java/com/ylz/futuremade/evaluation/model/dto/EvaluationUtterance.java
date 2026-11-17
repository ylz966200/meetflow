package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;

/** 评测用会议发言。 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationUtterance {
    /** 发言序号，从零开始。 */
    private Integer index;
    /** 说话人展示名称。 */
    private String speaker;
    /** 发言内容。 */
    private String text;
}
