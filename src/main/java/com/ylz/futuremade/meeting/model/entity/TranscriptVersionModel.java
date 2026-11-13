package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 会议文字稿版本表（mf_transcript_version）对应的数据模型。 */
@Getter
@Setter
public class TranscriptVersionModel {
    /** 会议 ID。 */
    private String meetingId;

    /** 文字稿版本号。 */
    private Integer version;

    /** 会议文字稿正文，属于敏感数据。 */
    private String content;

    /** 文字稿版本创建人用户 ID。 */
    private Long createdBy;

    /** 文字稿版本创建时间。 */
    private LocalDateTime createdAt;

}
