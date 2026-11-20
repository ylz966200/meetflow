package com.ylz.futuremade.evaluation.model.dto;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
/** 本地会议知识问答评测集。 */
@Getter @Setter
public class KnowledgeEvaluationDataset {
    /** 数据集版本。 */ private String datasetVersion;
    /** 数据集名称。 */ private String name;
    /** 数据和使用方式说明。 */ private String description;
    /** 建立日期。 */ private String createdAt;
    /** 标准答案审核状态。 */ private String reviewStatus;
    /** 来源条目下标规则。 */ private String sourceIndexConvention;
    /** 样本引用的会议版本。 */ private List<KnowledgeEvaluationMeeting> meetings;
    /** 待评测样本。 */ private List<KnowledgeEvaluationCase> cases;
}
