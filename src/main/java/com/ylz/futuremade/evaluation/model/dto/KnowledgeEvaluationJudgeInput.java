package com.ylz.futuremade.evaluation.model.dto;
import com.ylz.futuremade.knowledge.model.dto.MultiMeetingAnswer;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
/** 知识问答结果的模型评审输入。 */
@Getter @Setter
public class KnowledgeEvaluationJudgeInput {
    /** 评测样本和人工预设。 */ private KnowledgeEvaluationCase sample;
    /** 实际问答结果。 */ private MultiMeetingAnswer actual;
    /** 预期来源的召回检查。 */ private List<KnowledgeSourceCheck> sourceChecks;
}
