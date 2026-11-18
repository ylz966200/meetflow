package com.ylz.futuremade.knowledge.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.knowledge.enums.KnowledgeTaskType;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeIndexTaskConfig;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeIndexStatusView;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 复用后台任务和批次表，创建、查询与重试纪要索引任务。
 */
@Service
public class KnowledgeIndexTaskService {
    private final AiGenerationTaskMapper tasks;
    private final AiGenerationBatchMapper batches;
    private final ObjectMapper json;
    private final String embeddingModel;

    public KnowledgeIndexTaskService(AiGenerationTaskMapper tasks, AiGenerationBatchMapper batches, ObjectMapper json,
                                     @Value("${meetflow.ai.doubao.embedding.model:}") String embeddingModel) {
        this.tasks = tasks;
        this.batches = batches;
        this.json = json;
        this.embeddingModel = embeddingModel;
    }

    /**
     * 由确认纪要事务调用，任务和批次随纪要原子提交。
     */
    @Transactional
    public void create(String meetingId, int version, int transcriptVersion, int outlineVersion, long actor) {
        try {
            String key = UUID.nameUUIDFromBytes(("minutes-index:" + meetingId + ":" + version).getBytes(StandardCharsets.UTF_8)).toString();
            if (tasks.findByRequestKey(key) != null) return;
            var config = new KnowledgeIndexTaskConfig();
            config.setMinutesVersion(version);
            String input = json.writeValueAsString(config);
            var task = new AiGenerationTaskModel();
            task.setId(UUID.randomUUID().toString());
            task.setRequestKey(key);
            task.setMeetingId(meetingId);
            task.setTaskType(KnowledgeTaskType.MINUTES_INDEX.code());
            task.setStatus(GenerationTaskStatus.PENDING.code());
            task.setActorId(actor);
            task.setTranscriptVersion(transcriptVersion);
            task.setOutlineVersion(outlineVersion);
            task.setModelName(embeddingModel);
            task.setConfigJson(input);
            tasks.insert(task);
            var batch = new AiGenerationBatchModel();
            batch.setId(UUID.randomUUID().toString());
            batch.setTaskId(task.getId());
            batch.setBatchNo("00000001");
            batch.setStatus(GenerationBatchStatus.PENDING.code());
            batch.setInputJson(input);
            batch.setInputHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8))));
            batches.insert(batch);
        } catch (Exception ex) {
            throw new IllegalStateException("创建纪要索引任务失败", ex);
        }
    }

    /**
     * 查询当前会议的最近索引任务。
     */
    public AiGenerationTaskModel latest(String id) {
        return tasks.findLatest(id, KnowledgeTaskType.MINUTES_INDEX.code());
    }

    /**
     * 读取固定的纪要版本。
     */
    public int version(AiGenerationTaskModel task) {
        try {
            return json.readValue(task.getConfigJson(), KnowledgeIndexTaskConfig.class).getMinutesVersion();
        } catch (Exception ex) {
            throw new IllegalStateException("索引任务配置无效", ex);
        }
    }

    /**
     * 转换为现有知识库页面的状态编码。
     */
    public KnowledgeIndexStatusView view(AiGenerationTaskModel task) {
        var view = new KnowledgeIndexStatusView();
        view.setMinutesVersion(version(task));
        int status = task.getStatus();
        view.setStatus(status == GenerationTaskStatus.PARTIAL_FAILED.code() || status == GenerationTaskStatus.SOURCE_CHANGED.code() ? 5 : status);
        view.setErrorCode(task.getErrorCode());
        return view;
    }

    /**
     * 重置失败批次，保持其他后台任务相同的状态流转。
     */
    @Transactional
    public void retry(String id, long actor) {
        var latest = latest(id);
        if (latest == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "索引任务不存在");
        var task = tasks.lockById(latest.getId());
        if (task.getActorId() != actor) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅任务发起人可以重试");
        if (task.getStatus() != GenerationTaskStatus.PARTIAL_FAILED.code() || batches.resetFailed(task.getId(), GenerationBatchStatus.FAILED.code(), GenerationBatchStatus.PENDING.code()) == 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "没有可重试的索引任务");
        if (tasks.transition(task.getId(), GenerationTaskStatus.PARTIAL_FAILED.code(), GenerationTaskStatus.RUNNING.code(), null) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已变化");
    }
}
