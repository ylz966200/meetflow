package com.ylz.futuremade.meeting.model.entity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** 文字稿版本片段表对应的实体。 */
@Getter @Setter
public class TranscriptSegmentModel {
    /** 全局唯一的片段 ID。 */ private Long id;
    /** 所属会议 ID。 */ private String meetingId;
    /** 所属文字稿版本。 */ private Integer transcriptVersion;
    /** 版本内顺序，从零开始。 */ private Integer sequenceNo;
    /** 文字原文，可包含说话人。 */ private String content;
    /** 创建时间。 */ private LocalDateTime createdAt;
}
