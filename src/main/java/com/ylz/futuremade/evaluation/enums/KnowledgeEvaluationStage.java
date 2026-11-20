package com.ylz.futuremade.evaluation.enums;
/** 知识问答评测中可失败的阶段。 */
public enum KnowledgeEvaluationStage {
    /** 准备对话及执行问答。 */ ANSWER,
    /** 统计预期来源的召回情况。 */ SOURCE_CHECK,
    /** 调用模型评审。 */ JUDGE
}
