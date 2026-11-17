package com.ylz.futuremade.meeting.controller;

import com.ylz.futuremade.common.model.dto.ApiError;
import com.ylz.futuremade.meeting.model.dto.GenerateMinutesRequest;
import com.ylz.futuremade.meeting.model.dto.GenerationTaskView;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.MinutesTaskCreationService;
import com.ylz.futuremade.meeting.service.MinutesTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;

/**
 * 纪要后台任务控制器：创建、查询进度和重试失败批次。
 */
@RestController
@RequestMapping("/api/meetings/{id}/minutes/tasks")
public class MinutesTaskController {
    private final MeetingService meetings;
    private final MinutesTaskCreationService creation;
    private final MinutesTaskService tasks;
    private final boolean enabled;

    public MinutesTaskController(MeetingService meetings, MinutesTaskCreationService creation, MinutesTaskService tasks,
                                 @Value("${meetflow.minutes.background.enabled:false}") boolean enabled) {
        this.meetings = meetings;
        this.creation = creation;
        this.tasks = tasks;
        this.enabled = enabled;
    }

    /**
     * 创建任务并固定大纲、提示词和批次；仅会议编辑者可调用。
     */
    @PostMapping
    public GenerationTaskView create(@PathVariable String id, @RequestBody GenerateMinutesRequest request, Principal principal,
                                     @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) throws Exception {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "后台任务尚未启用");
        if (request.getOutlineVersion() == null || request.getExpectedVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本不能为空");
        long actor = meetings.userId(principal.getName());
        var task = creation.create(id, actor, request.getOutlineVersion(), request.getExpectedVersion(), requestKey == null ? UUID.randomUUID().toString() : requestKey);
        return tasks.get(id, task.getId(), actor);
    }

    /**
     * 查询当前会议指定任务进度。
     */
    @GetMapping("/{taskId}")
    public GenerationTaskView get(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return tasks.get(id, taskId, meetings.userId(principal.getName()));
    }

    /**
     * 查询当前会议最近的纪要任务。
     */
    @GetMapping("/latest")
    public GenerationTaskView latest(@PathVariable String id, Principal principal) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "后台任务尚未启用");
        return tasks.latest(id, meetings.userId(principal.getName()));
    }

    /**
     * 重试发起人创建的失败任务。
     */
    @PostMapping("/{taskId}/retry")
    public GenerationTaskView retry(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return tasks.retry(id, taskId, meetings.userId(principal.getName()));
    }

    /**
     * 返回安全的业务错误。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason()));
    }
}
