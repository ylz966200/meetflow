package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 研究助手生成的一项研究步骤，状态仅表示模型报告的进度。 */
@Getter
@Setter
public class ResearchTodo {
    /** 步骤内容。 */
    private String content;
    /** 状态编码：1 待处理、2 进行中、3 已完成。 */
    private Integer status;
}
