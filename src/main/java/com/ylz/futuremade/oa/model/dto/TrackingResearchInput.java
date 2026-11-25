package com.ylz.futuremade.oa.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 进展研究输入模板的固定变量，仅在提示词渲染边界转换为参数字典。
 */
@Getter
@Setter
public class TrackingResearchInput {
    /**
     * 本次待办追踪的研究目标。
     */
    private String objective;
    /**
     * 经人工确认的待办内容，作为不可信业务数据传入。
     */
    private String taskContent;
}
