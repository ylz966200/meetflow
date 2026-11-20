package com.ylz.futuremade.oa.controller;

import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.oa.model.dto.OaActionPreview;
import com.ylz.futuremade.oa.service.OaActionPreviewService;
import com.ylz.futuremade.oa.service.OaTaskService;
import com.ylz.futuremade.oa.model.dto.OaTaskCreateRequest;
import com.ylz.futuremade.oa.model.dto.OaTaskStatusRequest;
import com.ylz.futuremade.oa.model.dto.OaTaskView;
import com.ylz.futuremade.oa.model.entity.OaTask;
import com.ylz.futuremade.oa.model.entity.OaTaskAudit;
import com.ylz.futuremade.common.model.dto.ApiError;
import java.security.Principal;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.server.ResponseStatusException;

/** OA 行动项控制器，提供会议纪要行动项预览。 */
@RestController
@RequestMapping("/api/meetings/{id}/oa")
public class OaActionController {
    private final MeetingService meetings;
    private final OaActionPreviewService preview;
    private final OaTaskService tasks;

    public OaActionController(MeetingService meetings, OaActionPreviewService preview, OaTaskService tasks) {
        this.meetings = meetings;
        this.preview = preview;
        this.tasks = tasks;
    }

    /** 向前端返回业务校验失败原因。 */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> businessError(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(new ApiError(exception.getReason()));
    }

    /** 预览已确认纪要的行动项，复用纪要访问权限。 */
    @GetMapping("/actions")
    public OaActionPreview actions(@PathVariable String id, Principal principal) {
        return preview.preview(id, meetings.userId(principal.getName()));
    }

    /** 会议成员查看当前会议的本地待办。 */
    @GetMapping("/tasks")
    public List<OaTaskView> tasks(@PathVariable String id, Principal principal) {
        return tasks.list(id, meetings.userId(principal.getName()));
    }

    /** 会议编辑者确认来源和负责人后创建本地待办。 */
    @PostMapping("/tasks")
    public OaTask create(@PathVariable String id, @RequestBody OaTaskCreateRequest request, Principal principal) {
        return tasks.create(id, meetings.userId(principal.getName()), request);
    }

    /** 负责人或会议编辑者更新待办状态。 */
    @PatchMapping("/tasks/{taskId}/status")
    public OaTask changeStatus(@PathVariable String id, @PathVariable String taskId,
                               @RequestBody OaTaskStatusRequest request, Principal principal) {
        return tasks.changeStatus(id, taskId, meetings.userId(principal.getName()), request);
    }

    /** 会议成员查看本会议待办的状态变更记录。 */
    @GetMapping("/tasks/{taskId}/history")
    public List<OaTaskAudit> history(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return tasks.history(id, taskId, meetings.userId(principal.getName()));
    }

}
