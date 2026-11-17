package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import org.springframework.stereotype.Service;
import java.util.UUID;

/** 管理单次批次执行的领取、续租和结果写入权。 */
@Service
public class GenerationBatchLeaseService {
    private final AiGenerationBatchMapper batches;

    public GenerationBatchLeaseService(AiGenerationBatchMapper batches) {
        this.batches = batches;
    }

    /** 成功领取返回执行标识；其他执行者已领取时返回 null。 */
    public String claim(String batchId, String owner, int leaseSeconds) {
        requireValidLease(leaseSeconds);
        String token = UUID.randomUUID().toString();
        int changed = batches.claim(batchId, GenerationBatchStatus.PENDING.code(), GenerationBatchStatus.RUNNING.code(), owner, token, leaseSeconds);
        return changed == 1 ? token : null;
    }

    /** 仅有效租约持有者可以续租。 */
    public boolean renew(String batchId, String token, int leaseSeconds) {
        requireValidLease(leaseSeconds);
        return batches.renew(batchId, GenerationBatchStatus.RUNNING.code(), token, leaseSeconds) == 1;
    }

    /** 保存已校验结果；租约失效时返回 false，不允许覆盖其他执行者。 */
    public boolean complete(String batchId, String token, String output, Long callId) {
        return batches.finish(batchId, GenerationBatchStatus.RUNNING.code(), token, GenerationBatchStatus.SUCCEEDED.code(), output, callId, null) == 1;
    }

    /** 标记失败并保留安全错误码；租约失效时不能写入。 */
    public boolean fail(String batchId, String token, String errorCode, Long callId) {
        return batches.finish(batchId, GenerationBatchStatus.RUNNING.code(), token, GenerationBatchStatus.FAILED.code(), null, callId, errorCode) == 1;
    }

    private void requireValidLease(int leaseSeconds) {
        if (leaseSeconds <= 0) throw new IllegalArgumentException("租约时间必须大于零");
    }
}
