package com.ylz.futuremade.oa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.assistant.mapper.ToolPermissionMapper;
import com.ylz.futuremade.identity.mapper.AccountMapper;
import com.ylz.futuremade.common.logging.ErrorDiagnostics;
import com.ylz.futuremade.knowledge.model.dto.*;
import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.MinutesService;
import com.ylz.futuremade.oa.mapper.*;
import com.ylz.futuremade.oa.enums.TrackingJobStatus;
import com.ylz.futuremade.oa.model.dto.*;
import com.ylz.futuremade.oa.model.entity.*;

import java.util.*;
import java.util.concurrent.*;

import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 按会议持久化研究进度，使用令牌与租约在进程中断后恢复。
 */
@Service
public class TaskTrackingJobService {
    private final TaskTrackingJobMapper jobs;
    private final OaTaskMapper tasks;
    private final MeetingService meetings;
    private final MinutesService minutes;
    private final TaskTrackingService tracking;
    private final ObjectMapper json;
    private final ToolPermissionMapper permissions;
    private final AccountMapper accounts;
    private final ThreadPoolTaskExecutor pool;
    private final int concurrency;
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor();
    private final Set<String> active = ConcurrentHashMap.newKeySet();
    @Value("${meetflow.tracking.background.enabled:true}")
    private boolean enabled;
    @Value("${meetflow.tracking.lease-seconds:120}")
    private int leaseSeconds;
    @Value("${meetflow.knowledge.max-meetings:5}")
    private int maxMeetings;

    public TaskTrackingJobService(TaskTrackingJobMapper jobs, OaTaskMapper tasks, MeetingService meetings,
                                  MinutesService minutes, TaskTrackingService tracking, ObjectMapper json, ToolPermissionMapper permissions, AccountMapper accounts,
                                  @Value("${meetflow.tracking.max-concurrent:2}") int concurrency) {
        if (concurrency < 1 || concurrency > 10) throw new IllegalArgumentException("追踪并发数需为1至10");
        this.concurrency = concurrency;
        this.pool = new ThreadPoolTaskExecutor();
        this.pool.setCorePoolSize(concurrency);
        this.pool.setMaxPoolSize(concurrency);
        this.pool.setQueueCapacity(0);
        this.pool.setThreadNamePrefix("tracking-worker-");
        this.pool.initialize();
        this.jobs = jobs;
        this.tasks = tasks;
        this.meetings = meetings;
        this.minutes = minutes;
        this.tracking = tracking;
        this.json = json;
        this.permissions = permissions;
        this.accounts = accounts;
    }

    /**
     * 全部来源授权后原子创建一个追踪批次。
     */
    @Transactional
    public List<TaskTrackingJobView> create(String origin, String taskId, TaskTrackingRequest input, long actor, String username) throws Exception {
        if (!enabled)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "请先完成追踪任务迁移并启用后台追踪配置");
        access(origin, taskId, actor);
        Set<String> allowed = permissions.findEnabledToolNames(username);
        if (!allowed.containsAll(List.of("research_meeting_knowledge", "update_research_todos")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少会议研究工具权限");
        if (input.getMeetings() == null || input.getMeetings().isEmpty() || input.getMeetings().size() > maxMeetings)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 至 " + maxMeetings + " 场会议");
        String run = UUID.randomUUID().toString();
        Set<String> seen = new HashSet<>();
        for (KnowledgeMeetingSelection selection : input.getMeetings()) {
            if (selection == null || selection.getMeetingId() == null || selection.getMeetingId().isBlank() || selection.getMeetingId().equals(origin) || !seen.add(selection.getMeetingId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会议选择无效或重复");
            MinutesView view = minutes.get(selection.getMeetingId(), actor, null);
            if (selection.getMinutesVersion() != null && !selection.getMinutesVersion().equals(view.getVersion()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "纪要版本已变化");
            TaskTrackingJob job = new TaskTrackingJob();
            job.setId(UUID.randomUUID().toString());
            job.setRunId(run);
            job.setTaskId(taskId);
            job.setOriginMeetingId(origin);
            job.setMeetingId(selection.getMeetingId());
            job.setMinutesVersion(view.getVersion());
            job.setActorId(actor);
            job.setUsername(username);
            jobs.insert(job);
        }
        return list(origin, taskId, run, actor);
    }

    /**
     * 只返回本人批次，并重新校验每个来源的当前权限及版本。
     */
    public List<TaskTrackingJobView> list(String origin, String taskId, String run, long actor) throws Exception {
        access(origin, taskId, actor);
        List<TaskTrackingJobView> result = new ArrayList<>();
        for (TaskTrackingJob job : jobs.list(taskId, run, actor)) {
            MinutesView current = minutes.get(job.getMeetingId(), actor, null);
            TaskTrackingJobView view = new TaskTrackingJobView();
            view.setId(job.getId());
            view.setRunId(run);
            view.setMeetingId(job.getMeetingId());
            view.setStatus(job.getStatus());
            if (!job.getMinutesVersion().equals(current.getVersion())) {
                view.setMessage("纪要版本已变化，请重新选择会议发起研究");
            } else if (job.getStatus() == TrackingJobStatus.SUCCEEDED.code()) {
                view.setResult(json.readValue(job.getResultJson(), MeetingResearchAnswer.class));
            } else if (job.getStatus() == TrackingJobStatus.FAILED.code()) {
                String reason = job.getResultJson() == null ? TrackingFailureDetails.DEFAULT_MESSAGE
                        : json.readValue(job.getResultJson(), String.class);
                view.setMessage(reason + "（子任务编号 " + job.getId() + "）");
            }
            result.add(view);
        }
        return result;
    }

    /**
     * 保留成功会议，仅将失败子任务重新入队。
     */
    public void retry(String origin, String taskId, String run, long actor) throws Exception {
        list(origin, taskId, run, actor);
        jobs.retry(taskId, run, actor);
    }

    /**
     * 固定数量工作线程，数据库租约支持多实例竞争和重启恢复。
     */
    @Scheduled(fixedDelayString = "${meetflow.tracking.poll-delay-ms:3000}")
    public void poll() {
        if (!enabled) return;
        if (leaseSeconds < 6) throw new IllegalStateException("追踪租约至少六秒");
        for (TaskTrackingJob job : jobs.runnable()) {
            if (active.size() >= concurrency) break;
            if (!active.add(job.getId())) continue;
            try {
                pool.execute(() -> {
                    try {
                        execute(job);
                    } finally {
                        active.remove(job.getId());
                    }
                });
            } catch (org.springframework.core.task.TaskRejectedException ex) {
                active.remove(job.getId());
                throw ex;
            }
        }
    }

    void execute(TaskTrackingJob job) {
        String token = UUID.randomUUID().toString();
        if (jobs.claim(job.getId(), token, leaseSeconds) != 1) return;
        ScheduledFuture<?> renewal = heartbeat.scheduleWithFixedDelay(() -> {
            try {
                jobs.renew(job.getId(), token, leaseSeconds);
            } catch (RuntimeException ex) {
                ErrorDiagnostics.error("追踪任务续租失败", ex);
            }
        }, leaseSeconds / 3, leaseSeconds / 3, TimeUnit.SECONDS);
        try {
            AccountMapper.Account account = accounts.findByUsername(job.getUsername());
            if (account == null || !account.enabled() || account.id() != job.getActorId())
                throw new IllegalStateException("研究用户已失效");
            KnowledgeMeetingSelection selection = new KnowledgeMeetingSelection();
            selection.setMeetingId(job.getMeetingId());
            selection.setMinutesVersion(job.getMinutesVersion());
            TaskTrackingRequest input = new TaskTrackingRequest();
            input.setMeetings(List.of(selection));
            MeetingResearchAnswer answer = tracking.search(job.getOriginMeetingId(), job.getTaskId(), input, job.getActorId(), job.getUsername());
            jobs.finish(job.getId(), token, TrackingJobStatus.SUCCEEDED.code(), json.writeValueAsString(answer));
        } catch (Exception ex) {
            // 调度边界需要保存安全失败状态；完整异常通过统一脱敏诊断保留调用栈。
            String detail = TrackingFailureDetails.describe(ex);
            ErrorDiagnostics.error("追踪子任务 " + job.getId() + " 失败：" + detail, ex);
            try {
                jobs.finish(job.getId(), token, TrackingJobStatus.FAILED.code(), json.writeValueAsString(detail));
            } catch (Exception stateError) {
                ex.addSuppressed(stateError);
                ErrorDiagnostics.error("追踪失败状态保存失败", ex);
            }
        } finally {
            renewal.cancel(false);
        }
    }

    private void access(String origin, String taskId, long actor) {
        meetings.members(origin, actor);
        if (tasks.find(origin, taskId) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待办不存在");
    }

    /**
     * 关闭执行池，未完成任务在租约到期后可由新实例领取。
     */
    @PreDestroy
    public void close() {
        pool.shutdown();
        heartbeat.shutdownNow();
    }
}
