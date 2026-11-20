package com.ylz.futuremade.evaluation.model.entity;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;
/** 会议知识问答评测运行记录。 */
@Getter @Setter
public class KnowledgeEvaluationRun {
    /** 运行 ID。 */ private String id;
    /** 评测集版本。 */ private String datasetVersion;
    /** 样本 ID。 */ private String caseId;
    /** 执行管理员 ID。 */ private Long actorId;
    /** 会议 ID 列表，以逗号分隔。 */ private String meetingIds;
    /** 运行状态，参见 BusinessStatus。 */ private Integer status;
    /** 失败阶段。 */ private String errorStage;
    /** 样本与预期快照 JSON。 */ private String sampleJson;
    /** 实际问答结果 JSON。 */ private String resultJson;
    /** 来源召回检查 JSON。 */ private String sourceChecksJson;
    /** 问答调用关联标识。 */ private String requestKey;
    /** 模型评审意见。 */ private String review;
    /** 评审模型调用 ID。 */ private Long judgeCallId;
    /** 人工复核状态，参见 ManualReviewStatus。 */ private Integer manualReviewStatus;
    /** 人工复核备注。 */ private String manualReviewNote;
    /** 运行创建时间。 */ private LocalDateTime createdAt;
    /** 最近更新时间。 */ private LocalDateTime updatedAt;
}
