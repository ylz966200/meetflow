package com.ylz.futuremade.meeting.controller;

import com.ylz.futuremade.meeting.model.dto.ChunkSummaryView;
import com.ylz.futuremade.meeting.service.ChunkSummaryService;
import com.ylz.futuremade.meeting.service.MeetingService;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

/**
 * 会议录音的分段候选信息汇总接口，仅供会议组织者或编辑者使用。
 */
@RestController
@RequestMapping("/api/meetings/{meetingId}/transcriptions/{taskId}/chunks")
public class ChunkSummaryController {
    private final ChunkSummaryService summaries;
    private final MeetingService meetings;

    public ChunkSummaryController(ChunkSummaryService summaries, MeetingService meetings) {
        this.summaries = summaries;
        this.meetings = meetings;
    }

    /**
     * 归并全部有效分段生成大纲预览，保留候选编号；重复请求标识不会再次执行。
     */
    @PostMapping("/summary")
    public ChunkSummaryView generate(@PathVariable String meetingId, @PathVariable String taskId,
                                     @RequestHeader("Idempotency-Key") String key, Principal principal) {
        return summaries.generate(meetingId, taskId, meetings.userId(principal.getName()), key);
    }
}
