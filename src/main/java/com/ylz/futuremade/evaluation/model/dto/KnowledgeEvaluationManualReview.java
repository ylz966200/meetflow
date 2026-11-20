package com.ylz.futuremade.evaluation.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 管理员对评测结果的人工复核。 */
@Getter @Setter
public class KnowledgeEvaluationManualReview {
    /** 复核状态，1通过、2不通过。 */ private Integer status;
    /** 人工复核备注，最多 1000 字。 */ private String note;
}
