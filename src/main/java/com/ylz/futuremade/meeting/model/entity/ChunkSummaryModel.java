package com.ylz.futuremade.meeting.model.entity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** 单次分段候选信息汇总的持久化记录。 */
@Getter @Setter
public class ChunkSummaryModel {
    /** 请求及汇总记录 UUID。 */ private String id;
    /** 所属会议 ID。 */ private String meetingId;
    /** 来源转写任务 ID。 */ private String taskId;
    /** 发起用户 ID。 */ private Long actorId;
    /** 有序分段提取结果的 SHA-256 摘要。 */ private String sourceHash;
    /** 处理状态，参见 BusinessStatus。 */ private Integer status;
    /** 汇总模型调用记录 ID。 */ private Long aiCallId;
    /** 汇总草稿内容。 */ private String content;
    /** 创建时间。 */ private LocalDateTime createdAt;
}
