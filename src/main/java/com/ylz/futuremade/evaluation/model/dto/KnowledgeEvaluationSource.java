package com.ylz.futuremade.evaluation.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 人工预设的纪要来源。 */
@Getter @Setter
public class KnowledgeEvaluationSource {
    /** 数据集内会议简称。 */ private String meetingKey;
    /** 从零开始的纪要条目下标。 */ private Integer sectionIndex;
    /** 条目标题快照。 */ private String title;
}
