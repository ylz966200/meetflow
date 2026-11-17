package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 按需查询大纲原文的结果。 */
@Getter @Setter
public class MinutesSourceResult {
    /** 查询结果说明。 */ private String message;
    /** 按原始顺序排列的文字稿片段。 */ private List<OutlineSourceSegment> segments;
}
