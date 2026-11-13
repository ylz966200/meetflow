package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.model.dto.MinutesView;
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

    public GenerationResultService(OutlineService outlines, MinutesService minutes, GenerationRequestService requests) {
        this.outlines = outlines;
        this.minutes = minutes;
        this.requests = requests;
    }

    @Transactional
    public OutlineService.OutlineView outline(String key, String meetingId, long actor, int source, int expected, List<String> items) {
        var result = outlines.save(meetingId, actor, source, expected, items, false);
        requests.success(key, result.version());
        return result;
    }

    @Transactional
    public MinutesView minutes(String key, String meetingId, long actor, int source, int expected, String content) {
        var result = minutes.save(meetingId, actor, source, expected, content, false);
        requests.success(key, result.getVersion());
        return result;
    }
}
