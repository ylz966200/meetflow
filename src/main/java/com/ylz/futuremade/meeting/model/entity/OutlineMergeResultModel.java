package com.ylz.futuremade.meeting.model.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 大纲归并批次、拆分决策及阶段结果。 */
@Getter
@Setter
public class OutlineMergeResultModel {
    /** 所属大纲生成任务 ID。 */
    private String taskId;
    /** 批次输入及固定提示词版本，或归并阶段轮次的 SHA-256 摘要。 */
    private String inputHash;
    /** 已校验的归并结果或拆分决策 JSON，可能包含敏感会议数据。 */
    private String outputJson;
    /** 对应的 AI 调用记录 ID；阶段对应最近一次调用，无调用时为空。 */
    private Long aiCallId;
    /** 结果保存时间。 */
    private LocalDateTime createdAt;
}
