package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 会议纪要版本表 mf_minutes_version 对应的数据模型。 */
@Getter @Setter
public class MinutesVersionModel {
    /** 所属会议 ID。 */ private String meetingId;
    /** 会议内递增的纪要版本号。 */ private Integer version;
    /** 关联的已确认大纲版本号。 */ private Integer outlineVersion;
    /** 关联的文字稿版本号。 */ private Integer transcriptVersion;
    /** 纪要状态码，参见 BusinessStatus 枚举。 */ private Integer status;
    /** 会议纪要正文，属于敏感数据。 */ private String content;
    /** 版本创建人的用户 ID。 */ private Long createdBy;
    /** 版本创建时间。 */ private LocalDateTime createdAt;
}
