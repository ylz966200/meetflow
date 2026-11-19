package com.ylz.futuremade.knowledge.controller;

import com.ylz.futuremade.knowledge.model.dto.KnowledgeAnswer;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeQuestion;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeIndexStatusView;
import com.ylz.futuremade.knowledge.service.KnowledgeIndexTaskService;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.knowledge.service.MeetingKnowledgeService;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.MinutesService;
import com.ylz.futuremade.common.enums.BusinessStatus;

import java.security.Principal;

import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.ResponseEntity;
import com.ylz.futuremade.common.model.dto.ApiError;

/**
 * 当前会议知识索引和基于纪要的问答接口。
 */
@RestController
@RequestMapping("/api/meetings/{id}/knowledge")
public class MeetingKnowledgeController {
    private final MeetingKnowledgeService knowledge;
    private final MeetingService meetings;
    private final KnowledgeIndexTaskService tasks;
    private final MinutesService minutes;

    public MeetingKnowledgeController(MeetingKnowledgeService knowledge, MeetingService meetings, KnowledgeIndexTaskService tasks, MinutesService minutes) {
        this.knowledge = knowledge;
        this.meetings = meetings;
        this.tasks = tasks;
        this.minutes = minutes;
    }

    /**
     * 返回可展示的业务错误，不向页面暴露内部异常。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> businessError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason() == null ? "请求处理失败" : ex.getReason()));
    }

    /**
     * 为当前会议已确认纪要创建索引，仅有纪要编辑权限的成员可操作。
     */
    @PostMapping("/index")
    public void index(@PathVariable String id, Principal principal) {
        long actor = meetings.userId(principal.getName());
        var view = minutes.get(id, actor, null);
        if (!BusinessStatus.CONFIRMED.code().equals(view.getStatus()) || Boolean.TRUE.equals(view.getStale()) || view.getStructured() == null)
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "请先确认最新的结构化纪要");
        var previous = tasks.latest(id);
        if (previous != null && tasks.version(previous) == view.getVersion())
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "已有索引任务，请查看任务状态");
        tasks.create(id, view.getVersion(), view.getTranscriptVersion(), view.getOutlineVersion(), actor);
    }

    /**
     * 检索并回答当前会议问题，仅有纪要编辑权限的成员可操作。
     */
    @PostMapping("/answer")
    public KnowledgeAnswer answer(@PathVariable String id, @RequestBody KnowledgeQuestion input, Principal principal) throws Exception {
        long actor = meetings.userId(principal.getName());
        var view = minutes.get(id, actor, null);
        if (input.getMinutesVersion() != null && !view.getVersion().equals(input.getMinutesVersion()))
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "纪要版本已变化，请清空对话后重新提问");
        var task = tasks.latest(id);
        if (task == null || view.getVersion() != tasks.version(task) || task.getStatus() != GenerationTaskStatus.SUCCEEDED.code())
            throw new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT, "当前纪要索引尚未完成，请先查看索引状态");
        return knowledge.answer(id, actor, input.getQuestion(), input.getConversationId(), view.getVersion(), Boolean.TRUE.equals(input.getBypassCache()));
    }

    /**
     * 清除当前用户在指定会议和纪要版本下的短期会话记忆。
     */
    @PostMapping("/memory/clear")
    public void clearMemory(@PathVariable String id, @RequestBody KnowledgeQuestion input, Principal principal) {
        long actor = meetings.userId(principal.getName());
        meetings.requireOutlineEditor(id, actor, false);
        if (input.getMinutesVersion() == null || input.getMinutesVersion() < 1)
            throw new ResponseStatusException(org.springframework.http.HttpStatus.BAD_REQUEST, "纪要版本无效");
        knowledge.clearMemory(id, actor, input.getMinutesVersion(), input.getConversationId());
    }

    /**
     * 查询当前会议最新索引任务，复用纪要权限。
     */
    @GetMapping("/index-status")
    public KnowledgeIndexStatusView status(@PathVariable String id, Principal principal) {
        meetings.requireOutlineEditor(id, meetings.userId(principal.getName()), false);
        var task = tasks.latest(id);
        if (task == null)
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "暂无索引任务");
        return tasks.view(task);
    }

    /**
     * 重试最新的失败索引任务。
     */
    @PostMapping("/index-retry")
    public void retry(@PathVariable String id, Principal principal) {
        long actor = meetings.userId(principal.getName());
        meetings.requireOutlineEditor(id, actor, false);
        tasks.retry(id, actor);
    }
}
