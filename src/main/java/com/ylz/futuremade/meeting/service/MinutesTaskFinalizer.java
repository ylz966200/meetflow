package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.model.dto.MinutesActionItem;
import com.ylz.futuremade.meeting.model.dto.MinutesBatchItem;
import com.ylz.futuremade.meeting.model.dto.MinutesBatchResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;

/** 汇总有效子批次，并在同一事务中保存纪要和任务最终状态。 */
@Service
public class MinutesTaskFinalizer {
    private final AiGenerationTaskMapper tasks; private final AiGenerationBatchMapper batches;
    private final AdminMapper users;
    private final MeetingMapper meetings; private final MinutesService minutes; private final OutlineService outlines;
    private final MinutesBatchGenerator generator; private final ObjectMapper json;
    public MinutesTaskFinalizer(AiGenerationTaskMapper tasks,AiGenerationBatchMapper batches,AdminMapper users,MeetingMapper meetings,
            MinutesService minutes,OutlineService outlines,MinutesBatchGenerator generator,ObjectMapper json) {
        this.tasks=tasks;this.batches=batches;this.users=users;this.meetings=meetings;this.minutes=minutes;this.outlines=outlines;this.generator=generator;this.json=json;
    }
    /** 仅当所有有效批次完成且版本仍匹配时提交纪要。 */
    @Transactional(rollbackFor=Exception.class)
    public void finishIfReady(String taskId) throws Exception {
        var task=tasks.lockById(taskId);
        if(task==null || task.getStatus()!=GenerationTaskStatus.RUNNING.code()) return;
        var parts=batches.findByTask(taskId);
        if(parts.isEmpty()) throw new IllegalStateException("生成任务缺少批次");
        boolean stale=parts.stream().anyMatch(b->b.getStatus()==GenerationBatchStatus.FAILED.code() && ("SOURCE_CHANGED".equals(b.getErrorCode()) || "ACCESS_DENIED".equals(b.getErrorCode())));
        if(stale) { tasks.transition(taskId,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.SOURCE_CHANGED.code(),"SOURCE_CHANGED"); return; }
        boolean failed=parts.stream().anyMatch(b->b.getStatus()==GenerationBatchStatus.FAILED.code());
        if(failed) { tasks.transition(taskId,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.PARTIAL_FAILED.code(),"BATCH_FAILED"); return; }
        if(parts.stream().anyMatch(b->b.getStatus()==GenerationBatchStatus.PENDING.code() || b.getStatus()==GenerationBatchStatus.RUNNING.code())) return;
        if(parts.stream().anyMatch(b->b.getStatus()!=GenerationBatchStatus.SUCCEEDED.code() && b.getStatus()!=GenerationBatchStatus.SPLIT.code())) throw new IllegalStateException("批次状态无法汇总");
        var user=users.user(task.getActorId());
        if(user==null || !user.enabled()) { tasks.transition(taskId,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.SOURCE_CHANGED.code(),"ACCESS_DENIED"); return; }
        meetings.lockOwner(task.getMeetingId());
        com.ylz.futuremade.meeting.model.dto.OutlineView outline;
        try { outline=minutes.checkGeneration(task.getMeetingId(),task.getActorId(),task.getOutlineVersion(),task.getExpectedResultVersion(),outlines); }
        catch(ResponseStatusException ex) { tasks.transition(taskId,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.SOURCE_CHANGED.code(),"SOURCE_CHANGED"); return; }
        var items=new ArrayList<MinutesBatchItem>(); var actions=new ArrayList<MinutesActionItem>();
        for(var part:parts) {
            if(part.getStatus()!=GenerationBatchStatus.SUCCEEDED.code()) continue;
            var result=json.readValue(part.getOutputJson(),MinutesBatchResponse.class);
            items.addAll(result.getMinutes());actions.addAll(result.getActions());
        }
        var covered=new java.util.HashSet<Integer>();
        for(var item:items) if(item.getOutlineId()==null || !covered.add(item.getOutlineId())) throw new IllegalStateException("批次结果包含重复大纲");
        for(int index=1;index<=outline.getItems().size();index++) if(!covered.remove(index)) throw new IllegalStateException("批次结果遗漏大纲");
        if(!covered.isEmpty()) throw new IllegalStateException("批次结果包含未知大纲");
        items.sort(java.util.Comparator.comparing(MinutesBatchItem::getOutlineId));
        String content=generator.formatResult(outline,items,actions);
        if(content.length()>MinutesService.MAX_CONTENT_LENGTH) { tasks.transition(taskId,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.PARTIAL_FAILED.code(),"OUTPUT_TOO_LONG"); return; }
        var saved=minutes.saveGenerated(task.getMeetingId(),task.getActorId(),task.getOutlineVersion(),task.getExpectedResultVersion(),MinutesStructure.fromBatches(outline,items,actions));
        if(tasks.complete(taskId,GenerationTaskStatus.RUNNING.code(),GenerationTaskStatus.SUCCEEDED.code(),saved.getVersion())!=1)
            throw new IllegalStateException("任务完成状态异常");
    }
}
