package com.ylz.futuremade.meeting.controller;

import com.ylz.futuremade.common.enums.BusinessStatus;

import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.OutlineGenerator;
import com.ylz.futuremade.meeting.service.OutlineService;
import com.ylz.futuremade.meeting.service.GenerationRequestService;
import com.ylz.futuremade.meeting.service.GenerationResultService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.meeting.model.dto.OutlineVersionRequest;
import com.ylz.futuremade.meeting.model.dto.SaveOutlineRequest;

/**
 * 会议大纲控制器：提供大纲查询、AI 草稿生成、人工保存和确认接口。
 */
@RestController
@RequestMapping("/api/meetings/{id}/outline")
public class OutlineController {
    private final MeetingService meetings;
    private final OutlineService outlines;
    private final OutlineGenerator generator;
    private final PromptService prompts;
    private final GenerationRequestService requests;
    private final GenerationResultService results;

    public OutlineController(MeetingService meetings, OutlineService outlines, OutlineGenerator generator, PromptService prompts, GenerationRequestService requests, GenerationResultService results) {
        this.meetings = meetings;
        this.outlines = outlines;
        this.generator = generator;
        this.prompts = prompts;
        this.requests = requests;
        this.results = results;
    }

    /**
     * 查询会议大纲的最新版本或指定历史版本。
     */
    @GetMapping
    public OutlineService.OutlineView get(@PathVariable String id, @RequestParam(required = false) Integer version, Principal principal) {
        long actor = meetings.userId(principal.getName());
        return version == null ? outlines.latest(id, actor) : outlines.history(id, actor, version);
    }

    /**
     * 使用当前生效提示词和指定文字稿生成大纲草稿，并记录 AI 调用。
     */
    @PostMapping("/generate")
    public OutlineService.OutlineView generate(@PathVariable String id, @RequestBody OutlineVersionRequest input, Principal principal,
                                               @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        long actor = meetings.userId(principal.getName());
        meetings.requireOutlineEditor(id, actor, false);
        if (input.getTranscriptVersion() == null || input.getExpectedVersion() == null || input.getExpectedVersion() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本无效");
        var transcript = meetings.getTranscript(id, actor, input.getTranscriptVersion());
        if (transcript.content().length() > 12000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅支持不超过12000字符的文字稿");
        String key = requestKey == null ? java.util.UUID.randomUUID().toString() : requestKey;
        Integer replay = requests.begin(key, "MEETING_OUTLINE", id, actor, input.getTranscriptVersion(), input.getExpectedVersion());
        if (replay != null) return outlines.history(id, actor, replay);
        try {
            outlines.checkGeneration(id, actor, input.getTranscriptVersion(), input.getExpectedVersion());
            var prompt = prompts.active(PromptService.OUTLINE);
            long callId = prompts.startCall(prompt, id, input.getTranscriptVersion(), actor, key, null);
            long start = System.nanoTime();
            java.util.List<String> items;
            try {
                items = generator.generate(transcript.content(), prompt.getContent(), callId);
            } catch (OutlineGenerator.OutlineGenerationException ex) {
                prompts.finishCall(callId, BusinessStatus.FAILED.code(), (System.nanoTime() - start) / 1_000_000, "GENERATION_FAILED");
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "大纲生成失败，请稍后重试");
            }
            prompts.finishCall(callId, BusinessStatus.SUCCEEDED.code(), (System.nanoTime() - start) / 1_000_000, null);
            return results.outline(key, id, actor, input.getTranscriptVersion(), input.getExpectedVersion(), items);
        } catch (RuntimeException ex) {
            requests.failed(key);
            throw ex;
        }
    }

    /**
     * 保存人工编辑的大纲草稿，或由组织者确认大纲。
     */
    @PostMapping
    public OutlineService.OutlineView save(@PathVariable String id, @RequestBody SaveOutlineRequest input, Principal principal) {
        if (input.getTranscriptVersion() == null || input.getExpectedVersion() == null || input.getExpectedVersion() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本无效");
        return outlines.save(id, meetings.userId(principal.getName()), input.getTranscriptVersion(), input.getExpectedVersion(), input.getItems(), Boolean.TRUE.equals(input.getConfirm()));
    }

}
