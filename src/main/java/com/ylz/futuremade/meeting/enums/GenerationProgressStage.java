package com.ylz.futuremade.meeting.enums;

/**
 * 生成工作流检查点的稳定阶段编码，仅表示已保存位置，并非实时运行状态。
 */
public enum GenerationProgressStage {
    /**
     * 10：提取文字稿候选。
     */
    EXTRACT(10, "extract"),
    /**
     * 20：首轮归并。
     */
    MERGE(20, "mergeBatches"),
    /**
     * 30：跨批归并。
     */
    CROSS_MERGE(30, "crossMerge"),
    /**
     * 40：审核大纲。
     */
    REVIEW(40, "reviewOutline"),
    /**
     * 50：修正大纲。
     */
    REVISE(50, "reviseOutline"),
    /**
     * 60：保存大纲。
     */
    SAVE(60, "save"),
    /**
     * 70：处理大纲失败。
     */
    FAIL(70, "fail"),
    /**
     * 80：生成纪要批次。
     */
    MINUTES_GENERATE(80, "generateMinutesBatch"),
    /**
     * 90：汇总纪要。
     */
    MINUTES_FINISH(90, "finishMinutes");

    private final int code;
    private final String node;

    GenerationProgressStage(int code, String node) {
        this.code = code;
        this.node = node;
    }

    /**
     * 将框架节点名转换为稳定的接口编码；起点、终点和未知节点不显示为业务阶段。
     */
    public static Integer codeOf(String node) {
        for (GenerationProgressStage stage : values()) {
            if (stage.node.equals(node)) return stage.code;
        }
        return null;
    }
}
