package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 单个分段提取的候选信息，结论需人工核对。
 */
@Getter
@Setter
public class ChunkExtractionItem {
    /**
     * 候选议题名称。
     */
    private String topic;
    /**
     * 模型输出的信息类型名称，非持久化状态编码。
     */
    private String type;
    /**
     * 候选事实、观点或待确认事项。
     */
    private String content;
    /**
     * 原始转写片段序号，从 0 开始。
     */
    private List<Integer> evidenceIndices;
    /**
     * 信息仍需更多上下文或人工确认。
     */
    private Boolean needsReview;
}
