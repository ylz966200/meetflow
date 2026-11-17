package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 纪要后台任务创建时固定的分批和工具限制。 */
@Getter
@Setter
public class MinutesTaskConfig {
    /** 输入分批上限，单位为 UTF-8 字节。 */ private int batchMaxBytes;
    /** 每批工具调用次数日志提醒阈值，不限制执行。 */ private int maxToolCalls;
    /** 一次原文查询最多返回的片段数量。 */ private int maxSourceSegments;
    /** 一次原文查询最多返回的 UTF-16 单元数。 */ private int maxSourceChars;
}
