package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 分段候选信息汇总草稿。
 */
@Getter
@Setter
public class ChunkSummaryView {
    /**
     * 汇总请求 UUID。
     */
    private String id;
    /**
     * 来源转写任务 ID。
     */
    private String taskId;
    /**
     * 汇总模型调用记录 ID。
     */
    private Long aiCallId;
    /**
     * 待人工核对的汇总内容。
     */
    private String content;
}
