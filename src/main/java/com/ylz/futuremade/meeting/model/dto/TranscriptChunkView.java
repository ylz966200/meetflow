package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 文本分段预览，不代表已创建 AI 任务。
 */
@Getter
@Setter
public class TranscriptChunkView {
    /**
     * 所属转写任务 ID。
     */
    private String taskId;
    /**
     * 说话人校对快照版本。
     */
    private int speakerReviewVersion;
    /**
     * 本录音内分段序号，从 1 开始。
     */
    private int chunkIndex;
    /**
     * 本段负责处理的正文发言。
     */
    private List<ChunkUtterance> utterances;
    /**
     * 仅供理解的上一段末尾发言，不属于本段正文。
     */
    private List<ChunkUtterance> contextBefore;
    /**
     * 正文文字长度，不含说话人、序号、上下文和提示词。
     */
    private int textChars;
}
