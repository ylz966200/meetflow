package com.ylz.futuremade.knowledge.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 会议研究结果及助手报告的研究步骤。 */
@Getter
@Setter
public class MeetingResearchAnswer extends MultiMeetingAnswer {
    /** 最后一次更新的研究步骤，不代表经过程序验证的事实。 */
    private List<ResearchTodo> todos;
}
