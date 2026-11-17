package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 待人工审核的行动项候选。 */
@Getter @Setter
public class MinutesActionItem {
    /** 所属大纲序号。 */ private Integer outlineId;
    /** 会议明确提出的行动内容。 */ private String content;
    /** 明确提及的负责人，不明确时为空。 */ private String assignee;
    /** 明确提及的截止时间，不明确时为空。 */ private String deadline;
    /** 实际引用的文字稿片段序号，从零开始。 */ private List<Integer> sourceSegmentIds;
    /** 行动内容或责任信息是否需要核实。 */ private Boolean needsReview;
}
