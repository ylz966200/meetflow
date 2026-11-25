package com.ylz.futuremade.oa.controller;

import com.ylz.futuremade.common.model.dto.ApiError;
import com.ylz.futuremade.knowledge.model.dto.MeetingResearchAnswer;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.oa.model.dto.TaskTrackingEntry;
import com.ylz.futuremade.oa.model.dto.TaskTrackingLinkRequest;
import com.ylz.futuremade.oa.model.dto.TaskTrackingRequest;
import com.ylz.futuremade.oa.service.TaskTrackingService;

import java.security.Principal;

import com.ylz.futuremade.oa.model.dto.TaskTrackingJobView;
import com.ylz.futuremade.oa.service.TaskTrackingJobService;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * 会议待办的跨会议研究和人工确认来源接口。
 */
@RestController
@RequestMapping("/api/meetings/{id}/oa/tasks/{taskId}/tracking")
public class TaskTrackingController {
    private final TaskTrackingService tracking;
    private final MeetingService meetings;
    private final TaskTrackingJobService jobs;

    public TaskTrackingController(TaskTrackingService tracking, MeetingService meetings, TaskTrackingJobService jobs) {
        this.tracking = tracking;
        this.meetings = meetings;
        this.jobs = jobs;
    }

    /**
     * 返回业务错误。
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> error(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason()));
    }

    /**
     * 研究用户选择的会议，不修改待办状态。
     */
    @PostMapping("/search")
    public MeetingResearchAnswer search(@PathVariable String id, @PathVariable String taskId, @RequestBody TaskTrackingRequest input, Principal principal) throws Exception {
        return tracking.search(id, taskId, input, meetings.userId(principal.getName()), principal.getName());
    }

    /**
     * 创建持久化追踪批次，会议范围由用户显式指定。
     */
    @PostMapping("/runs")
    public List<TaskTrackingJobView> createRun(@PathVariable String id, @PathVariable String taskId, @RequestBody TaskTrackingRequest input, Principal principal) throws Exception {
        return jobs.create(id, taskId, input, meetings.userId(principal.getName()), principal.getName());
    }

    /**
     * 查询本人追踪批次的进度和已完成结果。
     */
    @GetMapping("/runs/{runId}")
    public List<TaskTrackingJobView> run(@PathVariable String id, @PathVariable String taskId, @PathVariable String runId, Principal principal) throws Exception {
        return jobs.list(id, taskId, runId, meetings.userId(principal.getName()));
    }

    /**
     * 仅重新执行当前批次中失败的会议。
     */
    @PostMapping("/runs/{runId}/retry")
    public void retry(@PathVariable String id, @PathVariable String taskId, @PathVariable String runId, Principal principal) throws Exception {
        jobs.retry(id, taskId, runId, meetings.userId(principal.getName()));
    }

    /**
     * 确认待办与会议纪要的关联。
     */
    @PostMapping("/links")
    public TaskTrackingEntry confirm(@PathVariable String id, @PathVariable String taskId, @RequestBody TaskTrackingLinkRequest input, Principal principal) {
        return tracking.confirm(id, taskId, input, meetings.userId(principal.getName()));
    }

    /**
     * 负责人或会议编辑者撤销错误关联。
     */
    @DeleteMapping("/links/{linkId}")
    public void remove(@PathVariable String id, @PathVariable String taskId, @PathVariable String linkId, Principal principal) {
        tracking.remove(id, taskId, linkId, meetings.userId(principal.getName()));
    }

    /**
     * 查看已确认关联，重新校验来源权限。
     */
    @GetMapping("/links")
    public List<TaskTrackingEntry> list(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return tracking.list(id, taskId, meetings.userId(principal.getName()));
    }
}
