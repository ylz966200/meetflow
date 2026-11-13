package com.ylz.futuremade.meeting.model.entity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** 会议录音文件表 mf_recording 对应的数据模型。 */
@Getter @Setter
public class RecordingModel {
    /** 录音文件唯一标识。 */ private String id;
    /** 所属会议 ID。 */ private String meetingId;
    /** TOS 对象 Key，不含签名链接。 */ private String objectKey;
    /** 原始文件名。 */ private String originalName;
    /** 文件大小，单位字节。 */ private Long fileSize;
    /** 音频容器格式。 */ private String format;
    /** 上传和提交状态码，参见 BusinessStatus 枚举。 */ private Integer status;
    /** 关联的识别任务 ID。 */ private String transcriptionTaskId;
    /** 查询时关联的最近转写任务状态码，不是录音表字段。 */ private Integer transcriptionStatus;
    /** 上传人用户 ID。 */ private Long createdBy;
    /** 录音记录创建时间。 */ private LocalDateTime createdAt;
    /** 录音记录更新时间。 */ private LocalDateTime updatedAt;
}
