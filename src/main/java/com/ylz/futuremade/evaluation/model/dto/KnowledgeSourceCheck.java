package com.ylz.futuremade.evaluation.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 一条预期纪要来源的召回检查。 */
@Getter @Setter
public class KnowledgeSourceCheck {
    /** 会议 ID。 */ private String meetingId;
    /** 预期纪要版本。 */ private Integer minutesVersion;
    /** 零起始纪要条目序号。 */ private Integer sectionIndex;
    /** 预期标题。 */ private String title;
    /** 是否出现在最终发送给模型的来源中。 */ private boolean found;
}
