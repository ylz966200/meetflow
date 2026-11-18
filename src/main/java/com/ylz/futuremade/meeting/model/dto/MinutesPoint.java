package com.ylz.futuremade.meeting.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 纪要中的一条讨论内容或会议结论。 */
@Getter @Setter
public class MinutesPoint {
    /** 讨论或结论的具体内容。 */
    private String content;
    /** 生成该条内容时引用的文字稿片段编号。 */
    private List<Integer> sourceSegmentIds;
    /** 信息或人工修改后的来源是否需要复核。 */
    private Boolean needsReview;
}
