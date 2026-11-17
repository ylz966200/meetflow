package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 单批大纲对应的纪要与行动项结果。
 */
@Getter
@Setter
public class MinutesBatchResponse {
    /**
     * 本批生成的纪要条目，须覆盖每条输入大纲。
     */
    private List<MinutesBatchItem> minutes;
    /**
     * 会议中明确提及的行动项；没有时为空数组。
     */
    private List<MinutesActionItem> actions;
}
