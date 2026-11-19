package com.ylz.futuremade.meeting.controller;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.ylz.futuremade.prompt.enums.PromptScenarioCode;


import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.OutlineGenerator;
import com.ylz.futuremade.meeting.service.TranscriptOutlineGenerator;
import com.ylz.futuremade.meeting.service.OutlineService;
import com.ylz.futuremade.meeting.service.GenerationRequestService;
import com.ylz.futuremade.meeting.service.GenerationResultService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.ylz.futuremade.prompt.service.PromptService;
import com.ylz.futuremade.meeting.model.dto.OutlineVersionRequest;
import com.ylz.futuremade.meeting.model.dto.OutlineView;
import com.ylz.futuremade.meeting.model.dto.SaveOutlineRequest;

/**
 * 会议大纲控制器：提供大纲查询、AI 草稿生成、人工保存和确认接口。
 */
@RestController
@RequestMapping("/api/meetings/{id}/outline")
public class OutlineController {
    private static final Logger log = LoggerFactory.getLogger(OutlineController.class);
    private final MeetingService meetings;
    private final OutlineService outlines;
    private final OutlineGenerator generator;
    private final TranscriptOutlineGenerator transcriptGenerator;
    private final PromptService prompts;
    private final GenerationRequestService requests;
    private final GenerationResultService results;

    public OutlineController(MeetingService meetings, OutlineService outlines, OutlineGenerator generator, PromptService prompts, GenerationRequestService requests, GenerationResultService results, TranscriptOutlineGenerator transcriptGenerator) {
        this.meetings = meetings;
        this.outlines = outlines;
        this.generator = generator;
        this.transcriptGenerator = transcriptGenerator;
        this.prompts = prompts;
        this.requests = requests;
        this.results = results;
    }

    /**
     * 查询会议大纲的最新版本或指定历史版本。
     */
    @GetMapping
    public OutlineView get(@PathVariable String id, @RequestParam(required = false) Integer version, Principal principal) {
        long actor = meetings.userId(principal.getName());
        return version == null ? outlines.latest(id, actor) : outlines.history(id, actor, version);
    }

    /** 分页查询大纲标题，后续页面使用首次返回的版本号。 */
    @GetMapping("/page")
    public com.ylz.futuremade.meeting.model.dto.OutlinePage page(@PathVariable String id, @RequestParam(required = false) Integer version,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "10") int size, Principal principal) {
        return outlines.page(id, meetings.userId(principal.getName()), version, page, size);
    }

    /** 按需查询指定大纲版本的候选内容与原文来源。 */
    @PostMapping("/sources")
    public com.ylz.futuremade.meeting.model.dto.OutlineSourceDetail sources(@PathVariable String id,
            @RequestBody com.ylz.futuremade.meeting.model.dto.OutlineSourceRequest request, Principal principal) {
        if (request.getVersion() == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大纲版本不能为空");
        return outlines.sourceDetail(id, meetings.userId(principal.getName()), request.getVersion(), request.getItemIds());
    }

    /**
     * 使用当前生效提示词和指定文字稿生成大纲草稿，并记录 AI 调用。
     */
    @PostMapping("/generate")
    public OutlineView generate(@PathVariable String id, @RequestBody OutlineVersionRequest input, Principal principal,
                                @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        long actor = meetings.userId(principal.getName());
        meetings.requireOutlineEditor(id, actor, false);
        if (input.getTranscriptVersion() == null || input.getExpectedVersion() == null || input.getExpectedVersion() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本无效");
        var transcript = meetings.getTranscript(id, actor, input.getTranscriptVersion());
        String key = requestKey == null ? java.util.UUID.randomUUID().toString() : requestKey;
        Integer replay = requests.begin(key, PromptScenarioCode.MEETING_OUTLINE.code(), id, actor, input.getTranscriptVersion(), input.getExpectedVersion());
        if (replay != null) return outlines.history(id, actor, replay);
        try {
            outlines.checkGeneration(id, actor, input.getTranscriptVersion(), input.getExpectedVersion());
            if (transcript.content().length() > 12000) {
                try {
                    var draft = transcriptGenerator.generate(transcript.content(), id, input.getTranscriptVersion(), actor, principal.getName(), key);
                    outlines.checkGeneration(id, actor, input.getTranscriptVersion(), input.getExpectedVersion());
                    return results.outlineWithSources(key, id, actor, input.getTranscriptVersion(), input.getExpectedVersion(), draft);
                } catch (ResponseStatusException ex) {
                    throw ex;
                } catch (Exception ex) {
                    com.ylz.futuremade.common.logging.ErrorDiagnostics.error("长文字稿大纲生成失败", ex);
                    if (ex instanceof com.ylz.futuremade.assistant.service.ModelOutputTruncatedException)
                        throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "单条文字稿片段的提取结果被截断，请调整分段长度或模型输出上限");
                    throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "长文字稿大纲生成失败，请检查分段与提示词配置后重试");
                }
            }
            var prompt = prompts.active(PromptScenarioCode.MEETING_OUTLINE.code());
            var audit = new AiCallContext(prompt, id, input.getTranscriptVersion(), actor, key, null);
            java.util.List<String> items;
            try {
                items = generator.generate(transcript.content(), audit);
            } catch (OutlineGenerator.OutlineGenerationException ex) {
                com.ylz.futuremade.common.logging.ErrorDiagnostics.error("会议大纲生成失败", ex);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "大纲生成失败，请稍后重试");
            }
            return results.outline(key, id, actor, input.getTranscriptVersion(), input.getExpectedVersion(), items);
        } catch (RuntimeException ex) {
            requests.failed(key);
            throw ex;
        }
    }

    /** 同步生成后只返回第一页，避免把来源快照发送给页面。 */
    @PostMapping("/generate-page")
    public com.ylz.futuremade.meeting.model.dto.OutlinePage generatePage(@PathVariable String id, @RequestBody OutlineVersionRequest input, Principal principal,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestKey) {
        var generated = generate(id, input, principal, requestKey);
        return outlines.page(id, meetings.userId(principal.getName()), generated.getVersion(), 1, 10);
    }

    /**
     * 保存人工编辑的大纲草稿，或由组织者确认大纲。
     */
    @PostMapping
    public OutlineView save(@PathVariable String id, @RequestBody SaveOutlineRequest input, Principal principal) {
        if (input.getTranscriptVersion() == null || input.getExpectedVersion() == null || input.getExpectedVersion() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本无效");
        return results.saveOutline(id, meetings.userId(principal.getName()), input.getTranscriptVersion(), input.getExpectedVersion(), input.getItems(), Boolean.TRUE.equals(input.getConfirm()), input.getItemSourceIds());
    }

    /** 保存草稿后只返回第一页，不向页面返回整份来源快照。 */
    @PostMapping("/save-page")
    public com.ylz.futuremade.meeting.model.dto.OutlinePage savePage(@PathVariable String id, @RequestBody SaveOutlineRequest input, Principal principal) {
        if (input.getTranscriptVersion() == null || input.getExpectedVersion() == null || input.getExpectedVersion() < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本无效");
        long actor = meetings.userId(principal.getName());
        var saved = results.saveOutline(id, actor, input.getTranscriptVersion(), input.getExpectedVersion(), input.getItems(), Boolean.TRUE.equals(input.getConfirm()), input.getItemSourceIds());
        return outlines.page(id, actor, saved.getVersion(), 1, 10);
    }

}
