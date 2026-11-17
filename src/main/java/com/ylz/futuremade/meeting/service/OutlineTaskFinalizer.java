package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.model.dto.TranscriptOutlineDraft;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 将完成的归并结果与大纲来源快照原子保存。 */
@Service
public class OutlineTaskFinalizer {
    private final AiGenerationTaskMapper tasks;private final AiGenerationBatchMapper batches;private final MeetingMapper meetings;
    private final AdminMapper users;private final OutlineService outlines;private final OutlineSourceService sources;private final ObjectMapper json;
    public OutlineTaskFinalizer(AiGenerationTaskMapper tasks,AiGenerationBatchMapper batches,MeetingMapper meetings,AdminMapper users,OutlineService outlines,OutlineSourceService sources,ObjectMapper json) {
        this.tasks=tasks;this.batches=batches;this.meetings=meetings;this.users=users;this.outlines=outlines;this.sources=sources;this.json=json;
    }
    /** 检查批次与版本，并在事务内写入大纲及任务状态。 */
    @Transactional(rollbackFor=Exception.class)
    public void finishIfReady(String id) throws Exception {
        var task=tasks.lockById(id);if(task==null || task.getStatus()!=GenerationTaskStatus.RUNNING.code())return;
        var parts=batches.findByTask(id);if(parts.isEmpty())throw new IllegalStateException("大纲任务缺少批次");
        if(parts.stream().anyMatch(b->b.getStatus()==GenerationBatchStatus.FAILED.code())) {
            boolean changed=parts.stream().anyMatch(b->"SOURCE_CHANGED".equals(b.getErrorCode()));
            tasks.transition(id,GenerationTaskStatus.RUNNING.code(),changed?GenerationTaskStatus.SOURCE_CHANGED.code():GenerationTaskStatus.PARTIAL_FAILED.code(),changed?"SOURCE_CHANGED":"BATCH_FAILED");return;
        }
        if(parts.stream().anyMatch(b->b.getStatus()!=GenerationBatchStatus.SUCCEEDED.code()))return;
        var user=users.user(task.getActorId());if(user==null || !user.enabled()){tasks.transition(id,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.SOURCE_CHANGED.code(),"ACCESS_DENIED");return;}
        meetings.lockOwner(task.getMeetingId());
        try { outlines.checkGeneration(task.getMeetingId(),task.getActorId(),task.getTranscriptVersion(),task.getExpectedResultVersion()); }
        catch(ResponseStatusException ex){
            int status=ex.getStatusCode().value();
            if(status!=403 && status!=404 && status!=409)throw ex;
            tasks.transition(id,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.SOURCE_CHANGED.code(),"SOURCE_CHANGED");return;
        }
        var grouped=parts.stream().filter(b->OutlineTaskService.GROUP_BATCH.equals(b.getBatchNo())).findFirst().orElseThrow();
        var draft=json.readValue(grouped.getOutputJson(),TranscriptOutlineDraft.class);
        var result=outlines.save(task.getMeetingId(),task.getActorId(),task.getTranscriptVersion(),task.getExpectedResultVersion(),draft.getTitles(),false,true);
        sources.save(task.getMeetingId(),result.getVersion(),draft.getSource());
        if(tasks.complete(id,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.SUCCEEDED.code(),result.getVersion())!=1)throw new IllegalStateException("任务完成状态异常");
    }
}
