package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.meeting.model.dto.OutlineView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 在同一事务内保存生成结果并完成请求去重记录。
 */
@Service
public class GenerationResultService {
    private final OutlineService outlines;
    private final MinutesService minutes;
    private final GenerationRequestService requests;
    private final OutlineSourceService sources;

    public GenerationResultService(OutlineService outlines, MinutesService minutes, GenerationRequestService requests, OutlineSourceService sources) {
        this.outlines = outlines;
        this.minutes = minutes;
        this.requests = requests;
        this.sources = sources;
    }

    @Transactional
    public OutlineView outline(String key, String meetingId, long actor, int source, int expected, List<String> items) {
        var result = outlines.save(meetingId, actor, source, expected, items, false);
        requests.success(key, result.getVersion());
        return result;
    }

    /**
     * 保存 AI 大纲及来源，和幂等结果在同一事务中提交。
     */
    @Transactional
    public OutlineView outlineWithSources(String key, String meetingId, long actor, int transcriptVersion, int expected, com.ylz.futuremade.meeting.model.dto.TranscriptOutlineDraft draft) {
        if (expected > 0) {
            var previous = outlines.history(meetingId, actor, expected);
            var oldSource = sources.find(meetingId, expected);
            if (previous.getTranscriptVersion() == transcriptVersion && previous.getItems().equals(draft.getTitles()) && sources.same(oldSource, draft.getSource())) {
                requests.success(key, previous.getVersion());
                return previous;
            }
        }
        var result = outlines.save(meetingId, actor, transcriptVersion, expected, draft.getTitles(), false, true);
        if (result.getVersion() == expected) throw new IllegalStateException("未生成新的大纲版本");
        sources.save(meetingId, result.getVersion(), draft.getSource());
        requests.success(key, result.getVersion());
        return outlines.history(meetingId, actor, result.getVersion());
    }

    /**
     * 编辑或确认时延续每个条目的来源，不因排序或改名丢失关联。
     */
    @Transactional
    public OutlineView saveOutline(String meetingId, long actor, int transcriptVersion, int expected, List<String> items, boolean confirm, List<List<String>> itemSourceIds) {
        if (expected > 0) outlines.history(meetingId, actor, expected);
        var previous = expected > 0 ? sources.find(meetingId, expected) : null;
        var remapped = previous == null ? null : sources.remap(previous, items, itemSourceIds);
        boolean sourceChanged = previous != null && !sources.same(previous, remapped);
        var result = outlines.save(meetingId, actor, transcriptVersion, expected, items, confirm, sourceChanged);
        if (remapped != null && result.getVersion() != expected) {
            sources.save(meetingId, result.getVersion(), remapped);
            return outlines.history(meetingId, actor, result.getVersion());
        }
        return result;
    }

    @Transactional
    public MinutesView minutes(String key, String meetingId, long actor, int source, int expected, String content) {
        var result = minutes.save(meetingId, actor, source, expected, content, false);
        requests.success(key, result.getVersion());
        return result;
    }

    /** 原子保存结构化生成结果和请求状态。 */
    @Transactional
    public MinutesView structuredMinutes(String key, String meetingId, long actor, int source, int expected, com.ylz.futuremade.meeting.model.dto.StructuredMinutes document) {
        var result = minutes.saveGenerated(meetingId, actor, source, expected, document);
        requests.success(key, result.getVersion());
        return result;
    }
}
