package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.enums.GenerationTaskStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.model.dto.OutlineView;
import com.ylz.futuremade.meeting.model.dto.TranscriptOutlineDraft;
import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import java.util.List;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Graph 结果保存边界：以短事务校验来源并原子更新大纲和任务。 */
@Service
@Slf4j
public class OutlineGraphFinalizer {
    private final AiGenerationTaskMapper tasks;
    private final AiGenerationBatchMapper batches;
    private final MeetingMapper meetings;
    private final AdminMapper users;
    private final OutlineService outlines;
    private final OutlineSourceService sources;

    public OutlineGraphFinalizer(AiGenerationTaskMapper tasks, AiGenerationBatchMapper batches, MeetingMapper meetings,
            AdminMapper users, OutlineService outlines, OutlineSourceService sources) {
        this.tasks = tasks; this.batches = batches; this.meetings = meetings; this.users = users;
        this.outlines = outlines; this.sources = sources;
    }

    /** 保存 Graph 归并结果；并发执行者只能有一次写入成功。 */
    @Transactional(rollbackFor = Exception.class)
    public void save(String taskId, TranscriptOutlineDraft draft, String token) throws Exception {
        AiGenerationTaskModel task = tasks.lockById(taskId);
        if (task == null || task.getStatus() != GenerationTaskStatus.RUNNING.code()) return;
        // 租约过期时不能让 Graph 将未保存的结果视为成功推进。
        if (!token.equals(tasks.lockActiveGraphToken(taskId))) throw new IllegalStateException("大纲 Graph 执行权已失效");
        // 等待同一会议的权限、文字稿和大纲写入完成后，再读取当前版本和权限。
        meetings.lockOwner(task.getMeetingId());
        List<AiGenerationBatchModel> parts = batches.findByTask(taskId);
        if (parts.isEmpty() || parts.stream().anyMatch(part -> part.getStatus() != GenerationBatchStatus.SUCCEEDED.code()))
            throw new IllegalStateException("大纲分段尚未全部完成");
        AdminMapper.UserRow user = users.user(task.getActorId());
        if (user == null || !user.enabled()) {
            tasks.transition(taskId, GenerationTaskStatus.RUNNING.code(), GenerationTaskStatus.SOURCE_CHANGED.code(), "ACCESS_DENIED");
            return;
        }
        try {
            outlines.checkGeneration(task.getMeetingId(), task.getActorId(), task.getTranscriptVersion(), task.getExpectedResultVersion());
        } catch (ResponseStatusException exception) {
            int status = exception.getStatusCode().value();
            if (status != 403 && status != 404 && status != 409) throw exception;
            tasks.transition(taskId, GenerationTaskStatus.RUNNING.code(), GenerationTaskStatus.SOURCE_CHANGED.code(), "SOURCE_CHANGED");
            return;
        }
        int resultVersion = 0;
        if (!draft.getTitles().isEmpty()) {
            OutlineView result = outlines.save(task.getMeetingId(), task.getActorId(), task.getTranscriptVersion(), task.getExpectedResultVersion(), draft.getTitles(), false, true);
            sources.save(task.getMeetingId(), result.getVersion(), draft.getSource());
            resultVersion = result.getVersion();
        }
        if (tasks.complete(taskId, GenerationTaskStatus.RUNNING.code(), GenerationTaskStatus.SUCCEEDED.code(), resultVersion) != 1)
            throw new IllegalStateException("任务完成状态异常");
        log.info("大纲结果已写入（事务内），taskId={}，resultVersion={}，titleCount={}",
                taskId, resultVersion, draft.getTitles().size());
    }

    /** 将提取阶段的失败反映到任务状态，保留批次记录供重试。 */
    @Transactional
    public void failIfNeeded(String taskId, String token) {
        AiGenerationTaskModel task = tasks.lockById(taskId);
        if (task == null || task.getStatus() != GenerationTaskStatus.RUNNING.code()) return;
        if (!token.equals(tasks.lockActiveGraphToken(taskId))) throw new IllegalStateException("大纲 Graph 执行权已失效");
        List<AiGenerationBatchModel> parts = batches.findByTask(taskId);
        boolean changed = parts.stream().anyMatch(part -> "SOURCE_CHANGED".equals(part.getErrorCode()));
        log.warn("大纲任务分段失败，taskId={}，sourceChanged={}", taskId, changed);
        tasks.transition(taskId, GenerationTaskStatus.RUNNING.code(), changed ? GenerationTaskStatus.SOURCE_CHANGED.code() : GenerationTaskStatus.PARTIAL_FAILED.code(), changed ? "SOURCE_CHANGED" : "BATCH_FAILED");
    }
}
