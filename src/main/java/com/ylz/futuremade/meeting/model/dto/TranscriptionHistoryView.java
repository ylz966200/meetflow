package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 一条录音的转写历史摘要，不包含会议正文。 */
@Getter @Setter
public class TranscriptionHistoryView {
    /** 转写任务 ID。 */ private String id;
    /** 任务状态码，参见 BusinessStatus。 */ private Integer status;
    /** 任务创建时间。 */ private LocalDateTime createdAt;
}
