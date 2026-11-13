package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 合并输入使用的转写与人工校对版本。 */
@Getter @Setter
public class TranscriptionSource {
    /** 识别任务 ID。 */ private String taskId;
    /** 来源录音 ID；旧链接任务可能为空。 */ private String recordingId;
    /** 获取输入时的说话人校对版本。 */ private Integer speakerReviewVersion;
}
