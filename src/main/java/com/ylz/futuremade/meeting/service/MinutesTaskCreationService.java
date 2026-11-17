package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/** 创建纪要生成任务并在同一事务中保存固定的批次输入。 */
@Service
public class MinutesTaskCreationService {
    private final AiGenerationTaskMapper tasks;
    private final AiGenerationBatchMapper batches;
    private final MeetingService meetings;
    private final MinutesService minutes;
    private final OutlineService outlines;
    private final MinutesBatchGenerator generator;
    private final PromptService prompts;
    private final ObjectMapper json;
    private final String modelName;

    public MinutesTaskCreationService(AiGenerationTaskMapper tasks, AiGenerationBatchMapper batches, MeetingService meetings, MinutesService minutes,
            OutlineService outlines, MinutesBatchGenerator generator, PromptService prompts, ObjectMapper json,
            @Value("${meetflow.ai.default-model:deepSeekChatModel}") String modelName) {
        this.tasks = tasks; this.batches = batches; this.meetings = meetings; this.minutes = minutes; this.outlines = outlines;
        this.generator = generator; this.prompts = prompts; this.json = json; this.modelName = modelName;
    }

    /** 幂等键相同且来源参数一致时返回已有任务；否则原子保存任务及全部批次。 */
    @Transactional(rollbackFor = Exception.class)
    public AiGenerationTaskModel create(String meetingId, long actor, int outlineVersion, int expectedVersion, String requestKey) throws Exception {
        validateKey(requestKey);
        meetings.requireOutlineEditor(meetingId, actor, false);
        var existing = tasks.findByRequestKey(requestKey);
        if (existing != null) return checkExisting(existing, meetingId, actor, outlineVersion, expectedVersion);
        var outline = minutes.checkGeneration(meetingId, actor, outlineVersion, expectedVersion, outlines);
        var plan = generator.prepareBatches(outline);
        if (plan.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大纲没有可生成的内容");
        var prompt = prompts.active(PromptScenarioCode.MEETING_MINUTES.code());
        var task = new AiGenerationTaskModel();
        task.setId(UUID.randomUUID().toString()); task.setRequestKey(requestKey); task.setMeetingId(meetingId);
        task.setTaskType(PromptScenarioCode.MEETING_MINUTES.code()); task.setStatus(GenerationTaskStatus.PENDING.code());
        task.setActorId(actor); task.setTranscriptVersion(outline.getTranscriptVersion()); task.setOutlineVersion(outlineVersion);
        task.setExpectedResultVersion(expectedVersion); task.setPromptVersionId(prompt.getId()); task.setModelName(modelName);
        task.setConfigJson(json.writeValueAsString(generator.taskConfig()));
        try {
            tasks.insert(task);
        } catch (DuplicateKeyException ex) {
            var concurrent = tasks.lockByRequestKey(requestKey);
            if (concurrent == null) throw ex;
            return checkExisting(concurrent, meetingId, actor, outlineVersion, expectedVersion);
        }
        for (int i = 0; i < plan.size(); i++) {
            String input = json.writeValueAsString(plan.get(i));
            var batch = new AiGenerationBatchModel(); batch.setId(UUID.randomUUID().toString()); batch.setTaskId(task.getId());
            batch.setBatchNo(String.format(java.util.Locale.ROOT, "%08d", i + 1)); batch.setStatus(GenerationBatchStatus.PENDING.code());
            batch.setInputJson(input); batch.setInputHash(sha256(input)); batches.insert(batch);
        }
        return task;
    }

    private AiGenerationTaskModel checkExisting(AiGenerationTaskModel task, String meetingId, long actor, int outlineVersion, int expectedVersion) {
        if (!Objects.equals(task.getMeetingId(), meetingId) || !Objects.equals(task.getActorId(), actor)
                || !Objects.equals(task.getTaskType(), PromptScenarioCode.MEETING_MINUTES.code())
                || !Objects.equals(task.getOutlineVersion(), outlineVersion) || !Objects.equals(task.getExpectedResultVersion(), expectedVersion))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请求标识已用于其他生成任务");
        return task;
    }

    private void validateKey(String key) {
        try { if (key == null || !UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请求标识必须是标准 UUID"); }
    }

    private String sha256(String input) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}
