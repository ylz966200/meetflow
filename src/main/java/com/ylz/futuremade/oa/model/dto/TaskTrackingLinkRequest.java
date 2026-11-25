package com.ylz.futuremade.oa.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 用户确认的待办进展来源。 */
@Getter @Setter
public class TaskTrackingLinkRequest {
    /** 来源会议编号。 */ private String sourceMeetingId;
    /** 来源纪要版本。 */ private Integer minutesVersion;
    /** 来源纪要条目序号，从零开始。 */ private Integer sectionIndex;
}
