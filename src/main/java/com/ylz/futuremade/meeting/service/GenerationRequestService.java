package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.mapper.GenerationRequestMapper;
import com.ylz.futuremade.meeting.model.entity.GenerationRequestModel;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.UUID;

/** 使用数据库唯一约束防止同一次 AI 生成请求重复执行。 */
@Service
public class GenerationRequestService {
    private final GenerationRequestMapper mapper;
    public GenerationRequestService(GenerationRequestMapper mapper) { this.mapper=mapper; }

    /** 返回已有成功结果的版本号；首次请求返回 null。 */
    public Integer begin(String key,String scenario,String meetingId,long actor,int source,int expected) {
        try { if(!UUID.fromString(key).toString().equals(key)) throw new IllegalArgumentException(); }
        catch(IllegalArgumentException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请求标识必须是标准 UUID"); }
        GenerationRequestModel row=new GenerationRequestModel();
        row.setRequestKey(key);row.setScenario(scenario);row.setMeetingId(meetingId);row.setActorId(actor);row.setSourceVersion(source);row.setExpectedVersion(expected);
        try { mapper.insert(row); return null; }
        catch(DuplicateKeyException ex) {
            var old=mapper.find(key);
            if(old==null || !scenario.equals(old.getScenario()) || !meetingId.equals(old.getMeetingId()) || old.getActorId()!=actor || old.getSourceVersion()!=source || old.getExpectedVersion()!=expected)
                throw new ResponseStatusException(HttpStatus.CONFLICT,"请求标识已被其他请求使用");
            if(BusinessStatus.SUCCEEDED.code().equals(old.getStatus()) && old.getResultVersion()!=null) return old.getResultVersion();
            if(BusinessStatus.RUNNING.code().equals(old.getStatus())) throw new ResponseStatusException(HttpStatus.TOO_EARLY,"该请求仍在处理，请稍后使用相同请求标识重试");
            throw new ResponseStatusException(HttpStatus.CONFLICT,"该请求已失败，请发起新的生成请求");
        }
    }
    public void success(String key,int version) { if(mapper.finish(key,BusinessStatus.SUCCEEDED.code(),version)!=1) throw new IllegalStateException("生成请求状态异常"); }
    public void failed(String key) { mapper.finish(key,BusinessStatus.FAILED.code(),null); }
}
