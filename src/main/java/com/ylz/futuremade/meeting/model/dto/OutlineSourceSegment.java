package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 大纲所引用的文字稿片段。 */
@Getter
@Setter
public class OutlineSourceSegment {
    /** 文字稿片段序号，从零开始。 */
    private Integer segmentIndex;
    /** 用户保存的文字稿片段正文。 */
    private String text;
}
