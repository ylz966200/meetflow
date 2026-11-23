package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** AI 后台生成任务表对应的数据模型。 */
@Getter
@Setter
public class AiGenerationTaskModel {
    /** 任务 ID。 */
    private String id;
    /** 幂等请求标识。 */
    private String requestKey;
    /** 所属会议 ID。 */
    private String meetingId;
    /** 生成任务类型编码。 */
    private String taskType;
    /** 任务状态码，参见 GenerationTaskStatus。 */
    private Integer status;
    /** 发起用户 ID。 */
    private Long actorId;
    /** 固定文字稿版本。 */
    private Integer transcriptVersion;
    /** 固定的已确认大纲版本。 */
    private Integer outlineVersion;
    /** 创建任务时的纪要版本。 */
    private Integer expectedResultVersion;
    /** 固定的提示词版本 ID。 */
    private Long promptVersionId;
    /** 固定模型标识。 */
    private String modelName;
    /** 固定生成配置 JSON，不含密钥。 */
    private String configJson;
    /** 成功保存后的纪要版本。 */
    private Integer resultVersion;
    /** 安全失败原因编码。 */
    private String errorCode;
    /** 当前大纲 Graph 执行实例标识。 */
    private String graphLeaseOwner;
    /** 当前大纲 Graph 租约到期时间。 */
    private LocalDateTime graphLeaseUntil;
    /** 当前大纲 Graph 执行令牌，用于阻止过期执行者写入。 */
    private String graphAttemptToken;
    /** 最近一次大纲 Graph 检查点，仅含节点及阶段引用。 */
    private String graphCheckpointJson;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 首次开始执行时间。 */
    private LocalDateTime startedAt;
    /** 完成或结束时间。 */
    private LocalDateTime finishedAt;
    /** 最后更新时间。 */
    private LocalDateTime updatedAt;
}
