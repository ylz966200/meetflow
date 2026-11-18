package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 一条大纲对应的待审核纪要。
 */
@Getter
@Setter
public class MinutesBatchItem {
    /**
     * 已确认大纲的序号。
     */
    private Integer outlineId;
    /**
     * 依据候选内容和原文生成的纪要。
     */
    private String content;
    /** 本大纲的讨论内容；旧版历史批次可能为空。 */
    private List<MinutesPoint> discussion;
    /** 本大纲已明确的会议结论。 */
    private List<MinutesPoint> conclusions;
    /**
     * 实际引用的文字稿片段序号，从零开始。
     */
    private List<Integer> sourceSegmentIds;
    /**
     * 内容是否需要人工核实。
     */
    private Boolean needsReview;
}
