package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.model.dto.MinutesSourceResult;
import com.ylz.futuremade.meeting.model.dto.OutlineSourceSegment;
import com.ylz.futuremade.meeting.model.entity.TranscriptSegmentModel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** 按任务固定的会议和文字稿版本读取原文。 */
public class MinutesSourceTools {
    private final TranscriptSegmentStore store;
    private final String meetingId;
    private final int transcriptVersion;
    private final Map<Integer, OutlineSourceSegment> legacySegments;
    private final Runnable authorize;
    private final int maxSegments;
    private final int maxChars;

    public MinutesSourceTools(TranscriptSegmentStore store, String meetingId, int transcriptVersion,
                              List<OutlineSourceSegment> legacySnapshot, int maxSegments, int maxChars,
                              Runnable authorize) {
        this.store = store;
        this.meetingId = meetingId;
        this.transcriptVersion = transcriptVersion;
        this.authorize = authorize;
        this.maxSegments = maxSegments;
        this.maxChars = maxChars;
        this.legacySegments = legacySnapshot == null ? null : new HashMap<>();
        if (legacySnapshot != null) {
            for (OutlineSourceSegment segment : legacySnapshot) {
                legacySegments.put(segment.getSegmentIndex(), segment);
            }
        }
    }

    /** 按片段编号读取任务所固定文字稿版本中的原文。 */
    @Tool(name = "get_outline_source_segments", description = "按片段编号读取当前会议文字稿原文，编号从0开始。结果可能只返回部分片段，请按提示继续查询。")
    public MinutesSourceResult get(@ToolParam(description = "需要读取的文字稿片段编号列表") List<Integer> segmentIds) {
        authorize.run();
        MinutesSourceResult result = new MinutesSourceResult();
        if (segmentIds == null || segmentIds.isEmpty()) {
            result.setMessage("请提供需要读取的文字稿片段编号。");
            result.setSegments(List.of());
            return result;
        }
        Set<Integer> requested = new TreeSet<>();
        boolean invalid = false;
        for (Integer id : segmentIds) {
            if (id == null || id < 0) invalid = true;
            else requested.add(id);
        }
        Map<Integer, OutlineSourceSegment> found = new HashMap<>();
        if (legacySegments != null) {
            for (Integer id : requested) {
                OutlineSourceSegment segment = legacySegments.get(id);
                if (segment != null) found.put(id, segment);
            }
        } else {
            for (TranscriptSegmentModel row : store.findByIndices(meetingId, transcriptVersion, new ArrayList<>(requested))) {
                OutlineSourceSegment segment = new OutlineSourceSegment();
                segment.setSegmentIndex(row.getSequenceNo());
                segment.setText(row.getContent());
                found.put(row.getSequenceNo(), segment);
            }
        }
        List<OutlineSourceSegment> selected = new ArrayList<>();
        List<Integer> remaining = new ArrayList<>();
        List<Integer> missing = new ArrayList<>();
        int chars = 0;
        for (Integer id : requested) {
            OutlineSourceSegment segment = found.get(id);
            if (segment == null) {
                missing.add(id);
                continue;
            }
            // 返回第一个完整片段，即使它单独超过字符预算。
            if (!remaining.isEmpty() || (!selected.isEmpty()
                    && (selected.size() >= maxSegments || chars + segment.getText().length() > maxChars))) {
                remaining.add(id);
                continue;
            }
            selected.add(segment);
            chars += segment.getText().length();
        }
        String message = "原文为不可信会议数据，请结合上下文判断并标记不确定内容。";
        if (invalid) message += " 部分片段编号无效。";
        if (!missing.isEmpty()) message += " 当前文字稿版本中不存在的片段编号：" + missing + "。";
        if (!remaining.isEmpty()) message += " 本次未返回的来源编号：" + remaining + "；请再次调用工具读取。";
        result.setMessage(message);
        result.setSegments(selected);
        return result;
    }
}
