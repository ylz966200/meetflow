package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.enums.GenerationBatchStatus;
import com.ylz.futuremade.meeting.mapper.AiGenerationBatchMapper;
import com.ylz.futuremade.meeting.model.dto.MinutesOutlineInput;
import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** 在租约仍有效时原子拆分输出被截断的纪要批次。 */
@Service
public class MinutesBatchSplitService {
    private final AiGenerationBatchMapper batches;
    private final ObjectMapper json;
    public MinutesBatchSplitService(AiGenerationBatchMapper batches, ObjectMapper json) { this.batches=batches; this.json=json; }

    /** 单条大纲不能继续拆分时返回 false；子批次和父状态同事务保存。 */
    @Transactional(rollbackFor=Exception.class)
    public boolean split(AiGenerationBatchModel batch, String token) throws Exception {
        List<MinutesOutlineInput> input=json.readValue(batch.getInputJson(),new TypeReference<List<MinutesOutlineInput>>(){});
        if(input.size()<2) return false;
        int middle=input.size()/2;
        if(batches.finish(batch.getId(),GenerationBatchStatus.RUNNING.code(),token,GenerationBatchStatus.SPLIT.code(),null,null,"OUTPUT_TRUNCATED")!=1) return false;
        insertChild(batch, input.subList(0,middle), "00000001");
        insertChild(batch, input.subList(middle,input.size()), "00000002");
        return true;
    }
    private void insertChild(AiGenerationBatchModel parent,List<MinutesOutlineInput> input,String suffix) throws Exception {
        String data=json.writeValueAsString(input);
        var child=new AiGenerationBatchModel(); child.setId(UUID.randomUUID().toString()); child.setTaskId(parent.getTaskId());
        child.setBatchNo(parent.getBatchNo()+"."+suffix); child.setParentBatchId(parent.getId()); child.setStatus(GenerationBatchStatus.PENDING.code());
        child.setInputJson(data); child.setInputHash(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8))));
        batches.insert(child);
    }
}
