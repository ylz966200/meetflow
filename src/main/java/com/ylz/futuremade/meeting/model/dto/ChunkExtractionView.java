package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 单个文字分段提取的候选信息结果。
 */
@Getter
@Setter
public class ChunkExtractionView {
    /**
     * 转写任务 ID，用于解释来源片段序号。
     */
    private String taskId;
    /**
     * 技术分段序号，从 1 开始。
     */
    private Integer chunkIndex;
    /**
     * 本次使用的说话人校对版本。
     */
    private Integer speakerReviewVersion;
    /**
     * 本次 AI 调用记录 ID。
     */
    private Long aiCallId;
    /**
     * 候选信息及来源，可能为空。
     */
    private List<ChunkExtractionItem> items;
    /** 长分段没有提取出候选时提示核对，不表示模型执行失败。 */
    private Boolean emptyNeedsReview;
}
