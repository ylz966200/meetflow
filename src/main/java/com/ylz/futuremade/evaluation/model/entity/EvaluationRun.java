package com.ylz.futuremade.evaluation.model.entity;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/** 一次会议样本自动评测运行记录。 */
@Getter
@Setter
public class EvaluationRun {
    /** 运行唯一标识。 */
    private String id;
    /** 随应用发布的评测集版本。 */
    private String datasetVersion;
    /** 数据集内的样本标识。 */
    private String caseId;
    /** 执行评测的管理员账号 ID。 */
    private Long actorId;
    /** 承载样本的测试会议 ID。 */
    private String meetingId;
    /** 运行状态，参见 BusinessStatus。 */
    private Integer status;
    /** 已生成的大纲，按 JSON 字符串保存。 */
    private String outlineJson;
    /** 生成的纪要及行动项。 */
    private String minutes;
    /** 豆包评审结果。 */
    private String review;
    /** 对应的豆包评审调用记录 ID。 */
    private Long judgeCallId;
    /** 不含业务正文的失败阶段。 */
    private String errorStage;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 最近更新时间。 */
    private LocalDateTime updatedAt;
}
