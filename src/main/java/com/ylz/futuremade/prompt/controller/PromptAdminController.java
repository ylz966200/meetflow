package com.ylz.futuremade.prompt.controller;

import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.prompt.model.entity.PromptCall;
import com.ylz.futuremade.prompt.model.entity.PromptRelease;
import com.ylz.futuremade.prompt.model.entity.PromptScenario;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.prompt.model.entity.AiCallContent;
import com.ylz.futuremade.prompt.model.dto.CreatePromptVersionRequest;
import com.ylz.futuremade.prompt.model.dto.PublishPromptRequest;
import com.ylz.futuremade.meeting.service.MeetingService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;
import com.ylz.futuremade.common.model.dto.ApiError;

/**
 * 提示词管理控制器：提供管理员对提示词场景、版本、发布记录和 AI 调用记录的管理接口。
 */
@RestController
@RequestMapping("/api/admin/prompts")
public class PromptAdminController {
    private final PromptService prompts;
    private final MeetingService meetings;

    public PromptAdminController(PromptService prompts, MeetingService meetings) {
        this.prompts = prompts;
        this.meetings = meetings;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> error(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ApiError(e.getReason() == null ? "操作失败" : e.getReason()));
    }

    /** 查询提示词业务场景及当前生效版本。 */
    @GetMapping
    public List<PromptScenario> scenarios() {
        return prompts.scenarios();
    }

    /** 查询指定场景的提示词历史版本。 */
    @GetMapping("/{code}/versions")
    public List<PromptVersion> versions(@PathVariable String code) {
        return prompts.versions(code);
    }

    /** 查询指定场景的发布和回滚记录。 */
    @GetMapping("/{code}/releases")
    public List<PromptRelease> releases(@PathVariable String code) {
        return prompts.releases(code);
    }

    /** 为指定场景创建新的提示词版本，创建后不会自动生效。 */
    @PostMapping("/{code}/versions")
    @ResponseStatus(HttpStatus.CREATED)
    public PromptVersion create(@PathVariable String code, @RequestBody CreatePromptVersionRequest request, Principal principal) {
        return prompts.create(code, request, meetings.userId(principal.getName()));
    }

    /** 发布指定版本；发布历史版本可用于回滚，并校验当前生效版本。 */
    @PostMapping("/{code}/publish")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void publish(@PathVariable String code, @RequestBody PublishPromptRequest request, Principal principal) {
        prompts.publish(code, request, meetings.userId(principal.getName()));
    }

    /** 分页查询 AI 调用记录。 */
    @GetMapping("/calls")
    public List<PromptCall> calls(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return prompts.calls(page, size);
    }

    /** 查询一次 AI 调用的敏感输入和原始输出，仅管理员可访问。 */
    @GetMapping("/calls/{callId}/content")
    public AiCallContent content(@PathVariable long callId) { return prompts.content(callId); }
}
