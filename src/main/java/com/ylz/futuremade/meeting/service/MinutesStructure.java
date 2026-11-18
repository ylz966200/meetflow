package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.model.dto.MinutesActionItem;
import com.ylz.futuremade.meeting.model.dto.MinutesBatchItem;
import com.ylz.futuremade.meeting.model.dto.MinutesSection;
import com.ylz.futuremade.meeting.model.dto.OutlineView;
import com.ylz.futuremade.meeting.model.dto.StructuredMinutes;
import java.util.ArrayList;
import java.util.List;

/** 纪要批次结果与展示文本的转换，不调用模型。 */
public final class MinutesStructure {
    private MinutesStructure() { }

    /** 将已校验的批次结果转换为按大纲组织的纪要。 */
    public static StructuredMinutes fromBatches(OutlineView outline, List<MinutesBatchItem> items, List<MinutesActionItem> actions) {
        var sections = new ArrayList<MinutesSection>();
        for (var item : items) {
            var section = new MinutesSection();
            section.setOutlineId(item.getOutlineId());
            section.setTitle(outline.getItems().get(item.getOutlineId() - 1));
            section.setContent(item.getContent());
            section.setSourceSegmentIds(item.getSourceSegmentIds());
            section.setNeedsReview(item.getNeedsReview());
            sections.add(section);
        }
        var result = new StructuredMinutes();
        result.setSections(sections);
        result.setActions(actions);
        return result;
    }

    /** 生成兼容旧接口的展示正文，结构化数据仍是该版本的编辑依据。 */
    public static String format(StructuredMinutes document) {
        var text = new StringBuilder("会议纪要\n");
        for (var section : document.getSections()) {
            text.append('\n').append(section.getOutlineId()).append(". ").append(section.getTitle()).append('\n')
                    .append(section.getContent()).append(Boolean.TRUE.equals(section.getNeedsReview()) ? "（待确认）" : "")
                    .append("\n来源片段：").append(section.getSourceSegmentIds()).append('\n');
        }
        text.append("\n行动项\n");
        for (var action : document.getActions()) {
            text.append("- ").append(action.getContent()).append("；负责人：").append(value(action.getAssignee()))
                    .append("；截止时间：").append(value(action.getDeadline()))
                    .append(Boolean.TRUE.equals(action.getNeedsReview()) ? "（待确认）" : "")
                    .append("；来源片段：").append(action.getSourceSegmentIds()).append('\n');
        }
        if (document.getActions().isEmpty()) text.append("未识别到明确行动项。\n");
        return text.toString().strip();
    }

    private static String value(String text) { return text == null || text.isBlank() ? "待确认" : text; }
}
