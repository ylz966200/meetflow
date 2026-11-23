package com.ylz.futuremade.meeting.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 大纲归并阶段需要的分段结果与来源。 */
@Getter
@Setter
public class OutlineMergeInput {
    /** 已完成分段的候选信息，按原文顺序排列。 */
    private List<ChunkExtractionView> results;
    /** 候选信息可引用的原始发言。 */
    private List<ChunkUtterance> utterances;
}
