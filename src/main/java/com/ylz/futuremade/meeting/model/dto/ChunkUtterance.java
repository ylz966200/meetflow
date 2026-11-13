package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 分段中的一段原始发言，可按原始位置准确还原。
 */
@Getter
@Setter
public class ChunkUtterance {
    /**
     * 来源转写任务 ID。
     */
    private String taskId;
    /**
     * 原始发言序号，从 0 开始。
     */
    private int segmentIndex;
    /**
     * 原识别片段起始时间，单位毫秒。
     */
    private long startMillis;
    /**
     * 原识别片段结束时间，单位毫秒。
     */
    private long endMillis;
    /**
     * 校对后的姓名或未确认的说话人标签。
     */
    private String speaker;
    /**
     * 原始发言文字。
     */
    private String text;
}
