package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.model.dto.GenerationTaskView;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.Objects;

/** 查询和重试会议纪要后台任务。 */
@Service
public class MinutesTaskService {
    private final AiGenerationTaskMapper tasks;private final AiGenerationBatchMapper batches;private final MeetingService meetings;
    public MinutesTaskService(AiGenerationTaskMapper tasks,AiGenerationBatchMapper batches,MeetingService meetings) {
        this.tasks=tasks;this.batches=batches;this.meetings=meetings;
    }
    /** 查询会议内任务；只向编辑者返回进度，不返回敏感快照。 */
    public GenerationTaskView get(String meetingId,String taskId,long actor) {
        meetings.requireOutlineEditor(meetingId,actor,false);
        return view(requireTask(tasks.findById(taskId),meetingId));
    }
    /** 查询会议最近的纪要生成任务。 */
    public GenerationTaskView latest(String meetingId,long actor) {
        meetings.requireOutlineEditor(meetingId,actor,false);
        return view(requireTask(tasks.findLatest(meetingId,PromptScenarioCode.MEETING_MINUTES.code()),meetingId));
    }
    /** 只重置失败批次，已成功的结果予以保留。 */
    @Transactional
    public GenerationTaskView retry(String meetingId,String taskId,long actor) {
        meetings.requireOutlineEditor(meetingId,actor,false);
        var task=requireTask(tasks.lockById(taskId),meetingId);
        if(!Objects.equals(task.getActorId(),actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN,"仅任务发起人可以重试");
        if(task.getStatus()!=GenerationTaskStatus.PARTIAL_FAILED.code()) throw new ResponseStatusException(HttpStatus.CONFLICT,"当前任务状态不支持重试");
        if(batches.resetFailed(taskId,GenerationBatchStatus.FAILED.code(),GenerationBatchStatus.PENDING.code())==0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"没有可重试的失败批次");
        if(tasks.transition(taskId,GenerationTaskStatus.PARTIAL_FAILED.code(),GenerationTaskStatus.RUNNING.code(),null)!=1)
            throw new IllegalStateException("任务状态更新失败");
        task.setStatus(GenerationTaskStatus.RUNNING.code());task.setErrorCode(null);return view(task);
    }
    private AiGenerationTaskModel requireTask(AiGenerationTaskModel task,String meetingId) {
        if(task==null || !meetingId.equals(task.getMeetingId()) || !PromptScenarioCode.MEETING_MINUTES.code().equals(task.getTaskType()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,"任务不存在");
        return task;
    }
    private GenerationTaskView view(AiGenerationTaskModel task) {
        var result=new GenerationTaskView();result.setTaskId(task.getId());result.setStatus(task.getStatus());
        result.setResultVersion(task.getResultVersion());result.setErrorCode(task.getErrorCode());
        var parts=batches.findByTask(task.getId());
        for(var part:parts) {
            if(part.getStatus()==GenerationBatchStatus.SPLIT.code())continue;
            result.setTotalBatches(result.getTotalBatches()+1);
            if(part.getStatus()==GenerationBatchStatus.SUCCEEDED.code()) result.setSucceededBatches(result.getSucceededBatches()+1);
            if(part.getStatus()==GenerationBatchStatus.FAILED.code()) result.setFailedBatches(result.getFailedBatches()+1);
        }
        return result;
    }
}
