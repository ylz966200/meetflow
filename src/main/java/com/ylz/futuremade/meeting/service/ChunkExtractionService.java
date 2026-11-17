package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.prompt.enums.PromptScenarioCode;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.mapper.ChunkExtractionMapper;
import com.ylz.futuremade.meeting.model.dto.ChunkExtractionRequest;
import com.ylz.futuremade.meeting.model.dto.ChunkExtractionView;
import com.ylz.futuremade.meeting.model.entity.ChunkExtractionModel;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import com.ylz.futuremade.meeting.model.dto.TranscriptChunkView;

/**
 * 单段 AI 候选信息提取；请求幂等并记录来源与模型调用。
 */
@Service
public class ChunkExtractionService {
    private final MeetingService access;
    private final TranscriptionService transcriptions;
    private final ChunkExtractionMapper extractions;
    private final ChunkExtractionAgent agent;
    private final PromptService prompts;
    private final ObjectMapper json;

    public ChunkExtractionService(MeetingService access, TranscriptionService transcriptions, ChunkExtractionMapper extractions,
                                  ChunkExtractionAgent agent, PromptService prompts, ObjectMapper json) {
        this.access = access;
        this.transcriptions = transcriptions;
        this.extractions = extractions;
        this.agent = agent;
        this.prompts = prompts;
        this.json = json;
    }

    public ChunkExtractionView extract(String meetingId, String taskId, int chunkIndex, long actor, String username, String key, ChunkExtractionRequest input) {
        access.requireOutlineEditor(meetingId, actor, false);
        try {
            if (key == null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求标识必须是标准 UUID");
        }
        if (input == null || input.getSpeakerReviewVersion() == null || input.getSpeakerReviewVersion() < 0 || chunkIndex < 1)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分段或说话人校对版本无效");
        var old = extractions.find(key);
        if (old != null) return replay(old, meetingId, taskId, chunkIndex, actor, input.getSpeakerReviewVersion());
        var context = transcriptions.extractionContext(meetingId, taskId, actor, chunkIndex);
        if (context.getChunk().getSpeakerReviewVersion() != input.getSpeakerReviewVersion())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "说话人校对已更新，请重新预览分段");
        var prompt = prompts.active(PromptScenarioCode.TRANSCRIPT_EXTRACTION.code());
        var record = new ChunkExtractionModel();
        record.setId(key);
        record.setMeetingId(meetingId);
        record.setTaskId(taskId);
        record.setChunkIndex(chunkIndex);
        record.setSpeakerReviewVersion(input.getSpeakerReviewVersion());
        record.setActorId(actor);
        record.setChunkFingerprint(fingerprint(context.getChunk()));
        record.setStatus(BusinessStatus.RUNNING.code());
        try {
            extractions.insert(record);
        } catch (DuplicateKeyException ex) {
            return replay(extractions.find(key), meetingId, taskId, chunkIndex, actor, input.getSpeakerReviewVersion());
        }
        Long callId = null;
        long start = System.nanoTime();
        try {
            callId = prompts.startCall(prompt, meetingId, null, actor, key, null);
            var result = agent.extract(context, prompt.getContent(), callId, username);
            result.setAiCallId(callId);
            String resultJson = json.writeValueAsString(result);
            if (extractions.finish(key, BusinessStatus.SUCCEEDED.code(), callId, resultJson) != 1)
                throw new IllegalStateException("提取记录状态异常");
            prompts.finishCall(callId, BusinessStatus.SUCCEEDED.code(), (System.nanoTime() - start) / 1_000_000, null);
            return result;
        } catch (Exception ex) {
            com.ylz.futuremade.common.logging.ErrorDiagnostics.error("会议分段提取失败", ex);
            if (callId != null)
                prompts.finishCall(callId, BusinessStatus.FAILED.code(), (System.nanoTime() - start) / 1_000_000, "EXTRACTION_FAILED");
            extractions.finish(key, BusinessStatus.FAILED.code(), callId, null);
            if (ex instanceof ResponseStatusException statusException) throw statusException;
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "分段提取失败，请检查模型及提示词配置后重试");
        }
    }

    /** 仅恢复当前用户在相同分段快照下已成功的结果，不触发模型调用。 */
    public List<ChunkExtractionView> completed(String meetingId, String taskId, long actor) {
        var chunks = transcriptions.previewChunks(meetingId, taskId, actor);
        var fingerprints = new HashMap<Integer, String>();
        for (var chunk : chunks) fingerprints.put(chunk.getChunkIndex(), fingerprint(chunk));
        var restored = new HashMap<Integer, ChunkExtractionView>();
        for (var record : extractions.completed(meetingId, taskId, actor, BusinessStatus.SUCCEEDED.code())) {
            if (restored.containsKey(record.getChunkIndex()) || !record.getChunkFingerprint().equals(fingerprints.get(record.getChunkIndex()))) continue;
            try {
                restored.put(record.getChunkIndex(), json.readValue(record.getResultJson(), ChunkExtractionView.class));
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("已保存的提取结果无法读取", ex);
            }
        }
        var result = new ArrayList<ChunkExtractionView>();
        for (var chunk : chunks) if (restored.containsKey(chunk.getChunkIndex())) result.add(restored.get(chunk.getChunkIndex()));
        return result;
    }

    private String fingerprint(TranscriptChunkView chunk) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(chunk)));
        } catch (JsonProcessingException | NoSuchAlgorithmException ex) {
            throw new IllegalStateException("无法计算分段快照摘要", ex);
        }
    }

    private ChunkExtractionView replay(ChunkExtractionModel value, String meetingId, String taskId, int chunkIndex, long actor, int version) {
        if (value == null || !meetingId.equals(value.getMeetingId()) || !taskId.equals(value.getTaskId()) || value.getChunkIndex() != chunkIndex || value.getActorId() != actor || value.getSpeakerReviewVersion() != version)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求标识已被其他请求使用");
        if (BusinessStatus.RUNNING.code().equals(value.getStatus()))
            throw new ResponseStatusException(HttpStatus.TOO_EARLY, "分段提取仍在处理，请稍后重试相同请求");
        if (!BusinessStatus.SUCCEEDED.code().equals(value.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该提取请求已失败，请重新发起");
        try {
            return json.readValue(value.getResultJson(), ChunkExtractionView.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("已保存的提取结果无法读取", ex);
        }
    }
}
