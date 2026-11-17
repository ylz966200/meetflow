package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** AI 后台生成任务批次表对应的数据模型。 */
@Getter
@Setter
public class AiGenerationBatchModel {
    /** 批次 ID。 */
    private String id;
    /** 所属任务 ID。 */
    private String taskId;
    /** 批次顺序路径。 */
    private String batchNo;
    /** 拆分来源父批次 ID。 */
    private String parentBatchId;
    /** 批次状态码，参见 GenerationBatchStatus。 */
    private Integer status;
    /** 固定输入快照，包含敏感会议数据。 */
    private String inputJson;
    /** 输入快照的 SHA-256 摘要。 */
    private String inputHash;
    /** 已校验的输出，包含敏感会议数据。 */
    private String outputJson;
    /** 最近一次 AI 调用记录 ID。 */
    private Long aiCallId;
    /** 已领取执行次数。 */
    private Integer attemptCount;
    /** 安全失败原因编码。 */
    private String errorCode;
    /** 当前执行实例标识。 */
    private String leaseOwner;
    /** 执行租约到期时间。 */
    private LocalDateTime leaseUntil;
    /** 当前执行尝试标识。 */
    private String attemptToken;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
