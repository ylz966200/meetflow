package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.enums.MinutesTaskFailureCode;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.dto.GenerationTaskView;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;

/**
 * 查询和重试会议纪要后台任务。
 */
@Service
@Slf4j
public class MinutesTaskService {
    private final AiGenerationTaskMapper tasks;
    private final AiGenerationBatchMapper batches;
    private final MeetingService meetings;
    private final com.fasterxml.jackson.databind.ObjectMapper json;

    public MinutesTaskService(AiGenerationTaskMapper tasks, AiGenerationBatchMapper batches, MeetingService meetings,
                              com.fasterxml.jackson.databind.ObjectMapper json) {
        this.tasks = tasks;
        this.batches = batches;
        this.meetings = meetings;
        this.json = json;
    }

    /**
     * 查询会议内任务；只向编辑者返回进度，不返回敏感快照。
     */
    public GenerationTaskView get(String meetingId, String taskId, long actor) {
        meetings.requireOutlineEditor(meetingId, actor, false);
        return view(requireTask(tasks.findById(taskId), meetingId));
    }

    /**
     * 查询会议最近的纪要生成任务。
     */
    public GenerationTaskView latest(String meetingId, long actor) {
        meetings.requireOutlineEditor(meetingId, actor, false);
        return view(requireTask(tasks.findLatest(meetingId, PromptScenarioCode.MEETING_MINUTES.code()), meetingId));
    }

    /**
     * 重试失败批次或 Graph 汇总，保留所有已成功批次的结果。
     */
    @Transactional
    public GenerationTaskView retry(String meetingId, String taskId, long actor) {
        meetings.requireOutlineEditor(meetingId, actor, false);
        AiGenerationTaskModel task = requireTask(tasks.lockById(taskId), meetingId);
        if (!Objects.equals(task.getActorId(), actor))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅任务发起人可以重试");
        if (task.getStatus() != GenerationTaskStatus.PARTIAL_FAILED.code())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前任务状态不支持重试");
        int resetCount = batches.resetFailed(taskId, GenerationBatchStatus.FAILED.code(), GenerationBatchStatus.PENDING.code());
        if (resetCount == 0 && !MinutesTaskFailureCode.GRAPH_FAILED.code().equals(task.getErrorCode()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "没有可重试的失败批次");
        // 从批次表重新路由，兼顾重新待执行的批次和可能无法读取的旧检查点。
        tasks.resetOutlineCheckpoint(taskId);
        if (tasks.transition(taskId, GenerationTaskStatus.PARTIAL_FAILED.code(), GenerationTaskStatus.RUNNING.code(), null) != 1)
            throw new IllegalStateException("任务状态更新失败");
        task.setStatus(GenerationTaskStatus.RUNNING.code());
        task.setErrorCode(null);
        log.info("纪要任务已重试（事务内），taskId={}，resetBatches={}", taskId, resetCount);
        return view(task);
    }

    private AiGenerationTaskModel requireTask(AiGenerationTaskModel task, String meetingId) {
        if (task == null || !meetingId.equals(task.getMeetingId()) || !PromptScenarioCode.MEETING_MINUTES.code().equals(task.getTaskType()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "任务不存在");
        return task;
    }

    private GenerationTaskView view(AiGenerationTaskModel task) {
        var result = new GenerationTaskView();
        result.setTaskId(task.getId());
        result.setStatus(task.getStatus());
        result.setResultVersion(task.getResultVersion());
        result.setErrorCode(task.getErrorCode());
        var parts = batches.findByTask(task.getId());
        for (var part : parts) {
            if (part.getStatus() == GenerationBatchStatus.SPLIT.code()) continue;
            result.setTotalBatches(result.getTotalBatches() + 1);
            if (part.getStatus() == GenerationBatchStatus.SUCCEEDED.code())
                result.setSucceededBatches(result.getSucceededBatches() + 1);
            if (part.getStatus() == GenerationBatchStatus.FAILED.code())
                result.setFailedBatches(result.getFailedBatches() + 1);
        }
        new OutlineCheckpointSaver(tasks, json).addProgress(result);
        return result;
    }
}
