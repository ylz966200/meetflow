package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 单次纪要批次执行及对应 AI 调用记录。 */
@Getter
@Setter
public class MinutesBatchExecutionResult {
    /** 已校验的结构化纪要与行动项。 */ private MinutesBatchResponse result;
    /** 本次调用的 AI 调用记录 ID。 */ private Long aiCallId;
}
