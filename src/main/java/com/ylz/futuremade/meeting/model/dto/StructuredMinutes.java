package com.ylz.futuremade.meeting.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 纪要版本的主题条目和行动项候选快照。
 */
@Getter
@Setter
public class StructuredMinutes {
    /**
     * 按大纲顺序排列的纪要条目。
     */
    private List<MinutesSection> sections;
    /**
     * AI 生成的行动项候选，需人工核对。
     */
    private List<MinutesActionItem> actions;
}
