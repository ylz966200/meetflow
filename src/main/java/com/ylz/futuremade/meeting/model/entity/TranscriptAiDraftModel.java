package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 文字稿 AI 草稿及来源快照表 mf_transcript_ai_draft。 */
@Getter @Setter
public class TranscriptAiDraftModel {
    /** 草稿与幂等请求 ID。 */ private String id;
    /** 所属会议 ID。 */ private String meetingId;
    /** 发起人用户 ID。 */ private Long actorId;
    /** 发起时的文字稿版本。 */ private Integer expectedVersion;
    /** 输入任务与版本的请求摘要。 */ private String requestHash;
    /** 转写任务及说话人校对版本的顺序快照。 */ private String sourcesJson;
    /** 生成状态码。 */ private Integer status;
    /** 关联 AI 调用记录 ID。 */ private Long aiCallId;
    /** AI 输出内容，属于敏感会议内容。 */ private String outputContent;
    /** 创建时间。 */ private LocalDateTime createdAt;
}
