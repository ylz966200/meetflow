package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 一次大纲归并的结果和调用记录。
 */
@Getter
@Setter
public class TopicAnalysisResult {
    /**
     * 大纲分组及原始候选内容的结构化快照。
     */
    private OutlineSourceSnapshot source;
    /**
     * 本次归并最后一次模型调用 ID；其他调用可通过请求标识查询。
     */
    private Long lastCallId;
}
