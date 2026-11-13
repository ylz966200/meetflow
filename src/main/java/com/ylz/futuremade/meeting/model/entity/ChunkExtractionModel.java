package com.ylz.futuremade.meeting.model.entity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** 单段提取记录表 mf_chunk_extraction。 */
@Getter @Setter
public class ChunkExtractionModel {
    /** 请求和记录 ID。 */ private String id;
    /** 所属会议 ID。 */ private String meetingId;
    /** 来源转写任务 ID。 */ private String taskId;
    /** 技术分段序号，从 1 开始。 */ private Integer chunkIndex;
    /** 所用说话人校对版本。 */ private Integer speakerReviewVersion;
    /** 发起人用户 ID。 */ private Long actorId;
    /** 处理状态码。 */ private Integer status;
    /** 对应 AI 调用记录 ID。 */ private Long aiCallId;
    /** 提取结果 JSON，包含敏感会议数据。 */ private String resultJson;
    /** 创建时间。 */ private LocalDateTime createdAt;
}
