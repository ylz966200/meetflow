package com.ylz.futuremade.evaluation.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 运行一条知识问答样本的请求。 */
@Getter @Setter
public class KnowledgeEvaluationRunRequest {
    /** 本地数据集中的样本 ID。 */ private String caseId;
}
