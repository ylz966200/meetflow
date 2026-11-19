package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;

import com.ylz.futuremade.prompt.enums.PromptScenarioCode;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.mapper.TranscriptAiDraftMapper;
import com.ylz.futuremade.meeting.model.dto.*;
import com.ylz.futuremade.meeting.model.entity.TranscriptAiDraftModel;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * 基于已校对的多个转写快照生成可追溯的文字稿草稿。
 */
@Service
public class TranscriptAiDraftService {
    /**
     * 单次 AI 输入长度上限，避免长会议超出当前处理能力。
     */
    private static final int MAX_INPUT_CHARS = 12000;
    private final MeetingService access;
    private final MeetingMapper meetings;
    private final TranscriptionService transcriptions;
    private final TranscriptAiDraftMapper drafts;
    private final PromptService prompts;
    private final TranscriptOrganizer organizer;
    private final ObjectMapper json;

    public TranscriptAiDraftService(MeetingService access, MeetingMapper meetings, TranscriptionService transcriptions, TranscriptAiDraftMapper drafts, PromptService prompts, TranscriptOrganizer organizer, ObjectMapper json) {
        this.access = access;
        this.meetings = meetings;
        this.transcriptions = transcriptions;
        this.drafts = drafts;
        this.prompts = prompts;
        this.organizer = organizer;
        this.json = json;
    }

    public OrganizedTranscriptView generate(String meetingId, long actor, String key, OrganizeTranscriptRequest input) {
        access.requireOutlineEditor(meetingId, actor, false);
        try {
            if (key == null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求标识必须是标准 UUID");
        }
        if (input == null || input.getExpectedVersion() == null || input.getExpectedVersion() < 0 || input.getTaskIds() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源任务或文字稿版本无效");
        try {
            String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(input)));
            var existing = drafts.find(key);
            if (existing != null) return replay(existing, meetingId, actor, hash);
            if (meetings.latestVersion(meetingId) != input.getExpectedVersion())
                throw new ResponseStatusException(HttpStatus.CONFLICT, "文字稿已更新，请刷新后重试");
            var request = new AssembleTranscriptionsRequest();
            request.setTaskIds(input.getTaskIds());
            var assembled = transcriptions.assemble(meetingId, actor, request);
            if (assembled.getContent().length() > MAX_INPUT_CHARS)
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "本次整理内容为 " + assembled.getContent().length() + " 个字符，超过当前上限 " + MAX_INPUT_CHARS + "；请选择较短的转写结果");
            var prompt = prompts.active(PromptScenarioCode.TRANSCRIPT_ORGANIZE.code());
            var draft = new TranscriptAiDraftModel();
            draft.setId(key);
            draft.setMeetingId(meetingId);
            draft.setActorId(actor);
            draft.setExpectedVersion(input.getExpectedVersion());
            draft.setRequestHash(hash);
            draft.setSourcesJson(json.writeValueAsString(assembled.getSources()));
            draft.setStatus(BusinessStatus.RUNNING.code());
            try {
                drafts.insert(draft);
            } catch (DuplicateKeyException ex) {
                return replay(drafts.find(key), meetingId, actor, hash);
            }
            var audit = new AiCallContext(prompt, meetingId, null, actor, key, null);
            try {
                String output = organizer.generate(assembled.getContent(), audit);
                if (drafts.finish(key, BusinessStatus.SUCCEEDED.code(), audit.getLastCallId(), output) != 1)
                    throw new IllegalStateException("草稿状态异常");
                return replay(drafts.find(key), meetingId, actor, hash);
            } catch (Exception ex) {
                com.ylz.futuremade.common.logging.ErrorDiagnostics.error("文字稿整理失败", ex);
                drafts.finish(key, BusinessStatus.FAILED.code(), audit.getLastCallId(), null);
                throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "文字稿整理失败，请稍后重试");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "文字稿整理请求无法处理");
        }
    }

    private OrganizedTranscriptView replay(TranscriptAiDraftModel draft, String meetingId, long actor, String hash) throws Exception {
        if (draft == null || !meetingId.equals(draft.getMeetingId()) || draft.getActorId() != actor || !hash.equals(draft.getRequestHash()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求标识已被其他请求使用");
        if (BusinessStatus.RUNNING.code().equals(draft.getStatus()))
            throw new ResponseStatusException(HttpStatus.TOO_EARLY, "整理任务正在处理，请稍后使用相同请求标识重试");
        if (!BusinessStatus.SUCCEEDED.code().equals(draft.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "整理任务已失败，请重新发起");
        var result = new OrganizedTranscriptView();
        result.setDraftId(draft.getId());
        result.setContent(draft.getOutputContent());
        result.setSources(json.readValue(draft.getSourcesJson(), new TypeReference<List<TranscriptionSource>>() {
        }));
        return result;
    }

    /**
     * 查看已保存文字稿版本的来源快照；会议成员可访问。
     */
    public TranscriptSourceView sources(String meetingId, long actor, int version) {
        if (version < 1) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文字稿版本无效");
        access.getTranscript(meetingId, actor, version);
        String id = meetings.transcriptDraftId(meetingId, version);
        var view = new TranscriptSourceView();
        view.setTranscriptVersion(version);
        view.setSources(List.of());
        if (id == null) return view;
        var draft = drafts.find(id);
        if (draft == null || !meetingId.equals(draft.getMeetingId()))
            throw new IllegalStateException("文字稿来源记录缺失");
        try {
            view.setDraftId(id);
            view.setAiCallId(draft.getAiCallId());
            view.setSources(json.readValue(draft.getSourcesJson(), new TypeReference<List<TranscriptionSource>>() {
            }));
            return view;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("文字稿来源无法读取", ex);
        }
    }
}
