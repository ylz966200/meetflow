package com.ylz.futuremade.meeting.transcript;

/**
 * 一段识别出的语音；说话人标签不代表已确认的真实身份。
 *
 * @param startMillis  片段开始时间，单位毫秒
 * @param endMillis    片段结束时间，单位毫秒
 * @param speakerLabel 语音服务返回的临时说话人标签
 * @param text         识别出的片段文字
 */
public record TranscriptSegment(long startMillis, long endMillis, String speakerLabel, String text) {
    public TranscriptSegment {
        if (startMillis < 0 || endMillis <= startMillis) {
            throw new IllegalArgumentException("Invalid segment time range");
        }
        if (speakerLabel == null || speakerLabel.isBlank() || text == null || text.isBlank()) {
            throw new IllegalArgumentException("Speaker label and text are required");
        }
    }
}
