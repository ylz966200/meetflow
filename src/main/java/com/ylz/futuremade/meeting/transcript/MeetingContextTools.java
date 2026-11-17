package com.ylz.futuremade.meeting.transcript;

import com.ylz.futuremade.meeting.model.dto.ChunkUtterance;
import com.ylz.futuremade.meeting.model.dto.MeetingContextResult;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * 绑定单次转写快照的只读工具；每个分段独立创建，禁止跨会议或访问后文。
 */
public class MeetingContextTools {
    private final List<ChunkUtterance> earlier;
    private final int maxUtterances;
    private final int maxChars;

    /**
     * 当前分段的起始序号是上界；只能查询之前的发言。
     */
    public MeetingContextTools(List<ChunkUtterance> snapshot, int currentStartIndex, MeetingContextProperties properties) {
        if (properties.getMaxUtterances() < 1 || properties.getMaxChars() < 1)
            throw new IllegalArgumentException("上下文工具配置无效");
        this.earlier = snapshot.stream().filter(item -> item.getSegmentIndex() < currentStartIndex).toList();
        this.maxUtterances = properties.getMaxUtterances();
        this.maxChars = properties.getMaxChars();
    }

    /**
     * 查询当前段之前的连续原文，结果按会议原始顺序排列。
     */
    @Tool(name = "get_previous_utterances", description = "读取当前分段之前的会议原文。beforeIndex 是原始片段序号（从0开始，不包含该序号）；返回结果按原顺序排列，不能读取当前段或后文。")
    public MeetingContextResult previous(@ToolParam(description = "起始边界片段序号，不包含该片段") int beforeIndex) {
        if (beforeIndex < 0) return result("片段序号不能小于零。", List.of(), false);
        return collect(earlier.stream().filter(item -> item.getSegmentIndex() < beforeIndex).toList());
    }

    /**
     * 精确包含词检索，只是候选结果；是否相关由模型结合原文判断。
     */
    @Tool(name = "search_meeting_context", description = "在当前分段之前的发言中按关键词查找原文，返回带来源编号的候选；该工具是字面匹配，不是语义检索。")
    public MeetingContextResult search(@ToolParam(description = "需要查找的关键词，如预算或场地；不超过100字符") String query) {
        if (query == null || query.isBlank() || query.length() > 100)
            return result("请提供不超过100字符的关键词。", List.of(), false);
        String keyword = query.strip().toLowerCase(Locale.ROOT);
        return collect(earlier.stream().filter(item -> item.getText().toLowerCase(Locale.ROOT).contains(keyword)).toList());
    }

    private MeetingContextResult collect(List<ChunkUtterance> candidates) {
        List<ChunkUtterance> selected = new ArrayList<>();
        int chars = 0;
        boolean truncated = false;
        for (int i = candidates.size() - 1; i >= 0; i--) {
            var item = candidates.get(i);
            if (selected.size() >= maxUtterances || chars + item.getText().length() > maxChars) {
                truncated = true;
                break;
            }
            selected.add(item);
            chars += item.getText().length();
        }
        Collections.reverse(selected);
        String message = selected.isEmpty() ? (truncated ? "匹配发言超出返回长度上限。" : "未找到匹配发言，请换词查询或标记待确认。") : "返回的是候选原文，请自行判断是否相关。";
        return result(message, List.copyOf(selected), truncated);
    }

    private MeetingContextResult result(String message, List<ChunkUtterance> items, boolean truncated) {
        var value = new MeetingContextResult();
        value.setMessage(message);
        value.setUtterances(items);
        value.setTruncated(truncated);
        return value;
    }
}
