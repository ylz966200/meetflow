package com.ylz.futuremade.evaluation.model.dto;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
/** 本次评测固定的样本和会议版本快照。 */
@Getter @Setter
public class KnowledgeEvaluationSampleSnapshot {
    /** 问题、历史和预期事实。 */ private KnowledgeEvaluationCase sample;
    /** 样本引用的会议及版本。 */ private List<KnowledgeEvaluationMeeting> meetings;
}
