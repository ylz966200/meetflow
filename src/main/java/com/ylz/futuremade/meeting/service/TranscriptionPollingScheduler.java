package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.mapper.TranscriptionTaskMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * 对未完成的转写任务定时查询；通过数据库租约避免多实例重复领取。
 */
@Component
public class TranscriptionPollingScheduler {
    private static final Logger log = LoggerFactory.getLogger(TranscriptionPollingScheduler.class);
    private final TranscriptionTaskMapper tasks;
    private final TranscriptionService service;
    private final boolean enabled;
    private final int intervalSeconds, maxAttempts, batchSize, leaseSeconds;

    public TranscriptionPollingScheduler(TranscriptionTaskMapper tasks, TranscriptionService service,
                                         @Value("${meetflow.speech.poll.enabled:true}") boolean enabled,
                                         @Value("${meetflow.speech.poll.interval-seconds:60}") int intervalSeconds,
                                         @Value("${meetflow.speech.poll.max-attempts:240}") int maxAttempts,
                                         @Value("${meetflow.speech.poll.batch-size:20}") int batchSize,
                                         @Value("${meetflow.speech.poll.lease-seconds:120}") int leaseSeconds) {
        if (intervalSeconds < 10 || maxAttempts < 1 || batchSize < 1 || batchSize > 100 || leaseSeconds < 60)
            throw new IllegalArgumentException("转写轮询配置无效");
        this.tasks = tasks;
        this.service = service;
        this.enabled = enabled;
        this.intervalSeconds = intervalSeconds;
        this.maxAttempts = maxAttempts;
        this.batchSize = batchSize;
        this.leaseSeconds = leaseSeconds;
    }

    /**
     * 每轮处理有限数量的任务；单次失败不会阻塞其余任务。
     */
    @Scheduled(fixedDelayString = "${meetflow.speech.poll.fixed-delay-ms:30000}", initialDelayString = "${meetflow.speech.poll.initial-delay-ms:30000}")
    public void poll() {
        if (!enabled) return;
        try {
            service.requireConfigured();
        } catch (org.springframework.web.server.ResponseStatusException ex) {
            return;
        }
        for (var task : tasks.due(batchSize, maxAttempts)) {
            String token = UUID.randomUUID().toString();
            if (tasks.claim(task.getId(), token, leaseSeconds) != 1) continue;
            try {
                service.poll(task);
            } catch (Exception ex) {
                log.warn("自动查询转写任务失败，任务ID={}，稍后重试", task.getId());
            } finally {
                tasks.release(task.getId(), token, intervalSeconds);
            }
        }
    }
}
