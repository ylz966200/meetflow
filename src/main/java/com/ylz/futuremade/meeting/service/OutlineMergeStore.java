package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.mapper.AiGenerationTaskMapper;
import com.ylz.futuremade.meeting.mapper.OutlineMergeResultMapper;
import com.ylz.futuremade.meeting.model.entity.OutlineMergeResultModel;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 保存已校验的归并批次及阶段结果，供同一任务恢复时复用。 */
@Service
public class OutlineMergeStore {
    private final AiGenerationTaskMapper tasks;
    private final OutlineMergeResultMapper results;
    private final ObjectMapper json;

    public OutlineMergeStore(AiGenerationTaskMapper tasks, OutlineMergeResultMapper results, ObjectMapper json) {
        this.tasks = tasks;
        this.results = results;
        this.json = json;
    }

    static String hash(String identity) throws NoSuchAlgorithmException {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(identity.getBytes(StandardCharsets.UTF_8)));
    }

    public OutlineMergeResultModel find(String taskId, String hash) {
        return results.find(taskId, hash);
    }

    /** 在任务行锁内校验租约，只保存当前执行者校验后的结果。 */
    @Transactional
    public void save(String taskId, String token, String hash, Object output, Long callId)
            throws JsonProcessingException {
        if (!token.equals(tasks.lockActiveGraphToken(taskId))) {
            throw new IllegalStateException("大纲归并结果写入时执行权已失效");
        }
        OutlineMergeResultModel result = new OutlineMergeResultModel();
        result.setTaskId(taskId);
        result.setInputHash(hash);
        result.setOutputJson(json.writeValueAsString(output));
        result.setAiCallId(callId);
        results.insert(result);
    }
}
