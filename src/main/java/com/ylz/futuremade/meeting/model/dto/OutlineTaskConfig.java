package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 大纲后台任务的固定生成配置。
 */
@Getter
@Setter
public class OutlineTaskConfig {
    /**
     * 任务创建时生效的候选信息归并提示词版本 ID。
     */
    private Long groupingPromptVersionId;
    /**
     * 任务创建时生效的归并审核提示词版本 ID；历史任务未配置时为空。
     */
    private Long reviewPromptVersionId;
    /** 自动修正最大轮数；历史任务为空时保持只读审核，零表示关闭自动修正。 */
    private Integer maxReviewRevisions;
}
