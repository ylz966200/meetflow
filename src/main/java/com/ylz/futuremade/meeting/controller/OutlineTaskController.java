package com.ylz.futuremade.meeting.controller;

import com.ylz.futuremade.common.model.dto.ApiError;
import com.ylz.futuremade.meeting.model.dto.GenerationTaskView;
import com.ylz.futuremade.meeting.model.dto.OutlineVersionRequest;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.OutlineTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.UUID;

/**
 * 会议大纲后台任务：创建、查询进度及重试。
 */
@RestController
@RequestMapping("/api/meetings/{id}/outline/tasks")
public class OutlineTaskController {
    private final MeetingService meetings;
    private final OutlineTaskService tasks;
    private final boolean enabled;

    public OutlineTaskController(MeetingService meetings, OutlineTaskService tasks,
            @Value("${meetflow.outline.background.enabled:${meetflow.minutes.background.enabled:false}}") boolean enabled) {
        this.meetings = meetings;
        this.tasks = tasks;
        this.enabled = enabled;
    }

    /**
     * 根据固定的文字稿和大纲版本创建任务。
     */
    @PostMapping
    public GenerationTaskView create(@PathVariable String id, @RequestBody OutlineVersionRequest input, Principal principal, @RequestHeader(value = "Idempotency-Key", required = false) String key) throws Exception {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "后台任务尚未启用");
        if (input.getTranscriptVersion() == null || input.getExpectedVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本不能为空");
        return tasks.create(id, meetings.userId(principal.getName()), input.getTranscriptVersion(), input.getExpectedVersion(), key == null ? UUID.randomUUID().toString() : key);
    }

    /**
     * 查询任务状态及进度。
     */
    @GetMapping("/{taskId}")
    public GenerationTaskView get(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return tasks.get(id, taskId, meetings.userId(principal.getName()));
    }

    /**
     * 查询当前会议最近的大纲任务。
     */
    @GetMapping("/latest")
    public GenerationTaskView latest(@PathVariable String id, Principal principal) {
        if (!enabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "后台任务尚未启用");
        return tasks.latest(id, meetings.userId(principal.getName()));
    }

    /**
     * 任务发起人重试失败批次。
     */
    @PostMapping("/{taskId}/retry")
    public GenerationTaskView retry(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return tasks.retry(id, taskId, meetings.userId(principal.getName()));
    }

    /**
     * 返回可展示的业务错误。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason()));
    }
}
