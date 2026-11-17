package com.ylz.futuremade.meeting.controller;

import com.ylz.futuremade.prompt.enums.PromptScenarioCode;

import com.ylz.futuremade.common.enums.BusinessStatus;

import com.ylz.futuremade.meeting.model.dto.GenerateMinutesRequest;
import com.ylz.futuremade.meeting.model.dto.SaveMinutesRequest;
import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.MinutesGenerator;
import com.ylz.futuremade.meeting.service.MinutesBatchGenerator;
import com.ylz.futuremade.meeting.service.MinutesService;
import com.ylz.futuremade.meeting.service.OutlineService;
import com.ylz.futuremade.meeting.service.GenerationRequestService;
import com.ylz.futuremade.meeting.service.GenerationResultService;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

/**
 * 会议纪要控制器：提供纪要生成、人工编辑、确认及历史版本查询接口。
 */
@RestController
@RequestMapping("/api/meetings/{id}/minutes")
public class MinutesController {
    private final MeetingService meetings;
    private final OutlineService outlines;
    private final MinutesService minutes;
    private final MinutesGenerator generator;
    private final MinutesBatchGenerator batchGenerator;
    private final PromptService prompts;
    private final GenerationRequestService requests;
    private final GenerationResultService results;

    public MinutesController(MeetingService meetings, OutlineService outlines, MinutesService minutes, MinutesGenerator generator, MinutesBatchGenerator batchGenerator, PromptService prompts, GenerationRequestService requests, GenerationResultService results) {
        this.meetings = meetings;
        this.outlines = outlines;
        this.minutes = minutes;
        this.generator = generator;
        this.batchGenerator = batchGenerator;
        this.prompts = prompts;
        this.requests = requests;
        this.results = results;
    }

    /** 返回可供页面展示的业务错误，不暴露内部异常。 */
    @ExceptionHandler(ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<com.ylz.futuremade.common.model.dto.ApiError> businessError(ResponseStatusException ex) {
        var error = new com.ylz.futuremade.common.model.dto.ApiError(ex.getReason() == null ? "请求处理失败" : ex.getReason());
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    /**
     * 查询最新纪要或指定历史版本，仅组织者和编辑者可访问。
     */
    @GetMapping
    public MinutesView get(@PathVariable String id, @RequestParam(required = false) Integer version, Principal principal) {
        return minutes.get(id, meetings.userId(principal.getName()), version);
    }

    /**
     * 根据最新已确认大纲生成纪要草稿，调用前后检查来源版本。
     */
    @PostMapping("/generate")
    public MinutesView generate(@PathVariable String id, @RequestBody GenerateMinutesRequest input, Principal principal,
                                @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        if (input.getOutlineVersion() == null || input.getExpectedVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本不能为空");
        long actor = meetings.userId(principal.getName());
        meetings.requireOutlineEditor(id, actor, false);
        String key = requestKey == null ? java.util.UUID.randomUUID().toString() : requestKey;
        Integer replay = requests.begin(key, PromptScenarioCode.MEETING_MINUTES.code(), id, actor, input.getOutlineVersion(), input.getExpectedVersion());
        if (replay != null) return minutes.get(id, actor, replay);
        try {
            var outline = minutes.checkGeneration(id, actor, input.getOutlineVersion(), input.getExpectedVersion(), outlines);
            var transcript = meetings.getTranscript(id, actor, outline.getTranscriptVersion());
            if (outline.getSources() != null) {
                try {
                    var prompt = prompts.active(PromptScenarioCode.MEETING_MINUTES.code());
                    String content = batchGenerator.generate(outline, prompt, id, actor, principal.getName(), key);
                    return results.minutes(key, id, actor, input.getOutlineVersion(), input.getExpectedVersion(), content);
                } catch (ResponseStatusException ex) {
                    throw ex;
                } catch (Exception ex) {
                    com.ylz.futuremade.common.logging.ErrorDiagnostics.error("分批生成会议纪要失败", ex);
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "分批生成纪要失败，请检查 AI 调用记录后重试", ex);
                }
            }
            if (transcript.content().length() > 12000)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅支持不超过12000字符的文字稿");
            var prompt = prompts.active(PromptScenarioCode.MEETING_MINUTES.code());
            long callId = prompts.startCall(prompt, id, outline.getTranscriptVersion(), actor, key, input.getOutlineVersion()), start = System.nanoTime();
            String content;
            try {
                content = generator.generate(transcript.content(), outline.getItems(), prompt.getContent(), callId);
            } catch (MinutesGenerator.MinutesGenerationException ex) {
                com.ylz.futuremade.common.logging.ErrorDiagnostics.error("会议纪要生成失败", ex);
                prompts.finishCall(callId, BusinessStatus.FAILED.code(), (System.nanoTime() - start) / 1_000_000, "GENERATION_FAILED");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "纪要生成失败，请稍后重试");
            }
            prompts.finishCall(callId, BusinessStatus.SUCCEEDED.code(), (System.nanoTime() - start) / 1_000_000, null);
            return results.minutes(key, id, actor, input.getOutlineVersion(), input.getExpectedVersion(), content);
        } catch (RuntimeException ex) {
            requests.failed(key);
            throw ex;
        }
    }

    /**
     * 保存纪要草稿；仅组织者可确认，须基于最新已确认大纲。
     */
    @PostMapping
    public MinutesView save(@PathVariable String id, @RequestBody SaveMinutesRequest input, Principal principal) {
        if (input.getOutlineVersion() == null || input.getExpectedVersion() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本不能为空");
        return minutes.save(id, meetings.userId(principal.getName()), input.getOutlineVersion(), input.getExpectedVersion(), input.getContent(), Boolean.TRUE.equals(input.getConfirm()));
    }
}
