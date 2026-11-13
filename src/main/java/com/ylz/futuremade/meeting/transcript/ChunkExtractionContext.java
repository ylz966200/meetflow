package com.ylz.futuremade.meeting.transcript;

import com.ylz.futuremade.meeting.model.dto.TranscriptChunkView;
import lombok.Getter;

/**
 * 单次提取使用的分段及同一份受限上下文快照。
 */
@Getter
public class ChunkExtractionContext {
    /**
     * 当前分段。
     */
    private final TranscriptChunkView chunk;
    /**
     * 限定在同一转写快照的工具。
     */
    private final MeetingContextTools tools;

    public ChunkExtractionContext(TranscriptChunkView chunk, MeetingContextTools tools) {
        this.chunk = chunk;
        this.tools = tools;
    }
}
