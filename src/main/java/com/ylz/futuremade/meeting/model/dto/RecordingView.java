package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** 对外展示的录音信息，不包含对象 Key 或临时链接。 */
@Getter @Setter
public class RecordingView {
    /** 录音 ID。 */ private String id;
    /** 原始文件名。 */ private String originalName;
    /** 文件大小，单位字节。 */ private Long fileSize;
    /** 音频格式。 */ private String format;
    /** 上传及转写提交状态码，参见 BusinessStatus 枚举。 */ private Integer status;
    /** 关联的转写任务 ID，未提交时为空。 */ private String transcriptionTaskId;
    /** 最近转写任务的状态码；未提交或任务不存在时为空。 */ private Integer transcriptionStatus;
    /** 上传时间。 */ private LocalDateTime createdAt;
}
