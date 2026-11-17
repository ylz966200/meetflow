package com.ylz.futuremade.evaluation.model.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** 会议评测样本。 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EvaluationCase {
    /** 样本唯一标识。 */
    private String id;
    /** 样本标题。 */
    private String title;
    /** 测试场景类型。 */
    private String category;
    /** 按顺序排列的虚构会议发言。 */
    private List<EvaluationUtterance> transcript;
    /** 人工预设标准答案。 */
    private EvaluationExpected expected;
}
