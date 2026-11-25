package com.ylz.futuremade.knowledge.controller;

import com.ylz.futuremade.knowledge.model.dto.MeetingResearchAnswer;
import com.ylz.futuremade.knowledge.model.dto.MultiMeetingQuestion;
import com.ylz.futuremade.knowledge.service.MeetingResearchService;
import com.ylz.futuremade.meeting.service.MeetingService;

import java.security.Principal;

import com.ylz.futuremade.common.model.dto.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 研究用户选定且有权限查看的会议纪要。
 */
@RestController
@RequestMapping("/api/knowledge/research")
public class MeetingResearchController {
    private final MeetingResearchService research;
    private final MeetingService meetings;

    public MeetingResearchController(MeetingResearchService research, MeetingService meetings) {
        this.research = research;
        this.meetings = meetings;
    }

    /**
     * 返回可供页面展示的业务错误。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> businessError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason()));
    }

    /**
     * 根据已选会议开展只读研究，并逐场校验权限和纪要版本。
     */
    @PostMapping
    public MeetingResearchAnswer research(@RequestBody MultiMeetingQuestion request, Principal principal) throws Exception {
        return research.research(request, meetings.userId(principal.getName()), principal.getName());
    }
}
