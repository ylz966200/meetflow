package com.ylz.futuremade.meeting.transcript;

import java.util.List;

/** 火山转写查询结果；原始响应可能包含敏感会议内容，不得写入日志。 */
public class TranscriptionQueryResult {
    /** 火山任务当前处理状态。 */
    private final VolcTranscriptionClient.State state;
    /** 已解析的会议语音片段，处理中或无语音时为空。 */
    private final List<TranscriptSegment> segments;
    /** 已完成查询返回的原始响应正文；处理中时为空，静音响应可能是空字符串。 */
    private final String rawResponse;

    public TranscriptionQueryResult(VolcTranscriptionClient.State state, List<TranscriptSegment> segments, String rawResponse) {
        this.state = state;
        this.segments = segments;
        this.rawResponse = rawResponse;
    }

    public VolcTranscriptionClient.State state() { return state; }
    public List<TranscriptSegment> segments() { return segments; }
    public String rawResponse() { return rawResponse; }
}
