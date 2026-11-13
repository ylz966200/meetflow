package com.ylz.futuremade.meeting.model.entity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** AI 生成请求去重记录表 mf_generation_request 对应的数据模型。 */
@Getter @Setter
public class GenerationRequestModel {
    /** 客户端生成的请求 UUID。 */ private String requestKey;
    /** 生成场景编码。 */ private String scenario;
    /** 关联会议 ID。 */ private String meetingId;
    /** 发起人的用户 ID。 */ private Long actorId;
    /** 来源文字稿或大纲版本号。 */ private Integer sourceVersion;
    /** 请求时的目标最新版本号。 */ private Integer expectedVersion;
    /** 请求状态码，参见 BusinessStatus 枚举。 */ private Integer status;
    /** 成功保存的目标版本号。 */ private Integer resultVersion;
    /** 请求创建时间。 */ private LocalDateTime createdAt;
    /** 状态最后更新时间。 */ private LocalDateTime updatedAt;
}
