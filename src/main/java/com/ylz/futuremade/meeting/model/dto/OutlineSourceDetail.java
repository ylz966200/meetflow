package com.ylz.futuremade.meeting.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 所选候选信息及其引用的文字稿片段。 */
@Getter @Setter
public class OutlineSourceDetail {
    /** 所选候选信息。 */ private List<TopicCandidate> items;
    /** 候选信息引用的文字稿片段。 */ private List<OutlineSourceSegment> segments;
}
