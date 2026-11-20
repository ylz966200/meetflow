package com.ylz.futuremade.knowledge.controller;

import com.ylz.futuremade.knowledge.model.dto.MultiMeetingAnswer;
import com.ylz.futuremade.knowledge.model.dto.MultiMeetingQuestion;
import com.ylz.futuremade.knowledge.service.MultiMeetingKnowledgeAccessService;
import com.ylz.futuremade.knowledge.service.MeetingKnowledgeService;
import com.ylz.futuremade.meeting.service.MeetingService;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import com.ylz.futuremade.common.model.dto.ApiError;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** 多会议知识问答接口，逐场校验纪要权限及索引状态。 */
@RestController
@RequestMapping("/api/knowledge")
public class MultiMeetingKnowledgeController {
    private final MeetingKnowledgeService knowledge;
    private final MeetingService meetings;
    private final MultiMeetingKnowledgeAccessService access;

    public MultiMeetingKnowledgeController(MeetingKnowledgeService knowledge, MeetingService meetings,
            MultiMeetingKnowledgeAccessService access) {
        this.knowledge = knowledge;
        this.meetings = meetings;
        this.access = access;
    }

    /** 返回可供页面展示的业务错误。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> businessError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason()));
    }

    /** 提问前校验全部会议，避免未授权资料进入模型或检索。 */
    @PostMapping("/answers")
    public MultiMeetingAnswer answer(@RequestBody MultiMeetingQuestion input, Principal principal) throws Exception {
        return access.answer(input, meetings.userId(principal.getName()));
    }

    /** 在原会议范围和纪要版本下清除短期会话记忆。 */
    @PostMapping("/memory/clear")
    public void clear(@RequestBody MultiMeetingQuestion input, Principal principal) {
        knowledge.clearMultiple(meetings.userId(principal.getName()), input);
    }
}
