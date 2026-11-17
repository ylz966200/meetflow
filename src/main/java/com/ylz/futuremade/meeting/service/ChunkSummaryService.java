package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.mapper.ChunkSummaryMapper;
import com.ylz.futuremade.meeting.model.dto.ChunkSummaryView;
import com.ylz.futuremade.meeting.model.entity.ChunkSummaryModel;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 将同一录音已完成的分段候选信息归并为可追溯的大纲草稿。
 */
@Service
public class ChunkSummaryService {
    private static final Logger log = LoggerFactory.getLogger(ChunkSummaryService.class);
    private final MeetingService access;
    private final TranscriptionService transcriptions;
    private final ChunkExtractionService extractions;
    private final ChunkSummaryMapper summaries;
    private final ObjectMapper json;
    private final TopicAnalysisService grouping;

    public ChunkSummaryService(MeetingService access, TranscriptionService transcriptions, ChunkExtractionService extractions,
                               ChunkSummaryMapper summaries, ObjectMapper json, TopicAnalysisService grouping) {
        this.access = access;
        this.transcriptions = transcriptions;
        this.extractions = extractions;
        this.summaries = summaries;
        this.json = json;
        this.grouping = grouping;
    }

    private ChunkSummaryView replay(ChunkSummaryModel row, String meetingId, String taskId, long actor) {
        if (row == null || !meetingId.equals(row.getMeetingId()) || !taskId.equals(row.getTaskId()) || row.getActorId() != actor)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求标识已被其他请求使用");
        if (BusinessStatus.RUNNING.code().equals(row.getStatus()))
            throw new ResponseStatusException(HttpStatus.TOO_EARLY, "汇总仍在处理中，请稍后重试");
        if (!BusinessStatus.SUCCEEDED.code().equals(row.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "汇总已失败，请重新发起");
        var result = new ChunkSummaryView();
        result.setId(row.getId());
        result.setTaskId(row.getTaskId());
        result.setAiCallId(row.getAiCallId());
        result.setContent(row.getContent());
        return result;
    }

    public ChunkSummaryView generate(String meetingId, String taskId, long actor, String key) {
        access.requireOutlineEditor(meetingId, actor, false);
        try {
            if (key == null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求标识必须是标准 UUID");
        }
        var existing = summaries.find(key);
        if (existing != null) return replay(existing, meetingId, taskId, actor);
        var chunks = transcriptions.previewChunks(meetingId, taskId, actor);
        var results = extractions.completed(meetingId, taskId, actor);
        if (chunks.isEmpty() || results.size() != chunks.size())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "存在未完成或已过期的分段提取，请先完成全部分段");
        try {
            var row = new ChunkSummaryModel();
            row.setId(key);
            row.setMeetingId(meetingId);
            row.setTaskId(taskId);
            row.setActorId(actor);
            row.setSourceHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(json.writeValueAsBytes(results))));
            row.setStatus(BusinessStatus.RUNNING.code());
            try {
                summaries.insert(row);
            } catch (DuplicateKeyException ex) {
                return replay(summaries.find(key), meetingId, taskId, actor);
            }
            try {
                var grouped = grouping.group(results, meetingId, actor, key);
                String currentHash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(json.writeValueAsBytes(extractions.completed(meetingId, taskId, actor))));
                if (!row.getSourceHash().equals(currentHash))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "分析期间分段结果已变化，请重新生成");
                if (summaries.finish(key, BusinessStatus.SUCCEEDED.code(), grouped.getLastCallId(), json.writeValueAsString(grouped.getSource())) != 1)
                    throw new IllegalStateException("汇总状态异常");
                return replay(summaries.find(key), meetingId, taskId, actor);
            } catch (Exception ex) {
                com.ylz.futuremade.common.logging.ErrorDiagnostics.error("分段内容合并失败", ex);
                summaries.finish(key, BusinessStatus.FAILED.code(), null, null);
                if (ex instanceof ResponseStatusException statusException) throw statusException;
                throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "分段内容合并失败，请稍后重试");
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("分段汇总请求无法处理", ex);
        }
    }
}
