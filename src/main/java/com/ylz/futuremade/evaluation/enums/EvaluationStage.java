package com.ylz.futuremade.evaluation.enums;

/** 评测运行可能失败的处理阶段，编码写入运行记录用于排查。 */
public enum EvaluationStage {
    /** 创建评测专用会议和文字稿。 */
    CREATE_MEETING,
    /** 分段提取并生成大纲。 */
    OUTLINE,
    /** 根据大纲生成纪要和行动项。 */
    MINUTES,
    /** 使用评审模型评价生成结果。 */
    JUDGE
}
