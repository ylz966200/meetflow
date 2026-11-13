package com.ylz.futuremade.prompt.service;

import com.ylz.futuremade.common.enums.BusinessStatus;

import com.ylz.futuremade.prompt.mapper.PromptMapper;
import com.ylz.futuremade.prompt.model.entity.PromptCall;
import com.ylz.futuremade.prompt.model.entity.PromptRelease;
import com.ylz.futuremade.prompt.model.entity.PromptScenario;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.prompt.model.entity.AiCallContent;
import com.ylz.futuremade.prompt.model.dto.CreatePromptVersionRequest;
import com.ylz.futuremade.prompt.model.dto.PublishPromptRequest;

import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.deepseek.api.DeepSeekApi;

@Service
public class PromptService {
    public static final String OUTLINE = "MEETING_OUTLINE";
    private final PromptMapper mapper;

    public PromptService(PromptMapper mapper) {
        this.mapper = mapper;
    }

    public List<PromptScenario> scenarios() {
        return mapper.scenarios();
    }

    public List<PromptVersion> versions(String code) {
        return mapper.versions(code);
    }

    public List<PromptRelease> releases(String code) {
        return mapper.releases(code);
    }

    @Transactional
    public PromptVersion create(String code, CreatePromptVersionRequest input, long actor) {
        if (input.getContent() == null || input.getContent().isBlank() || input.getContent().length() > 10000 || input.getChangeNote() == null || input.getChangeNote().isBlank() || input.getChangeNote().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "提示词和修改原因必填，长度分别不超过 10000 和 500 字符");
        if (mapper.lockScenario(code) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "场景不存在");
        PromptVersion version = new PromptVersion();
        version.setScenario(code);
        version.setVersion(mapper.latestVersion(code) + 1);
        version.setContent(input.getContent());
        version.setChangeNote(input.getChangeNote().strip());
        version.setCreatedBy(actor);
        mapper.insertVersion(version);
        return mapper.version(code, version.getId());
    }

    @Transactional
    public void publish(String code, PublishPromptRequest input, long actor) {
        if (input.getVersionId() == null || input.getReason() == null || input.getReason().isBlank() || input.getReason().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本和发布原因必填");
        PromptScenario scenario = mapper.lockScenario(code);
        if (scenario == null || mapper.version(code, input.getVersionId()) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "场景或版本不存在");
        if (!Objects.equals(scenario.getActiveVersionId(), input.getExpectedActiveVersionId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "生效版本已变化，请刷新后重试");
        if (Objects.equals(scenario.getActiveVersionId(), input.getVersionId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该版本已经生效");
        mapper.publish(code, input.getVersionId());
        mapper.releaseAudit(code, scenario.getActiveVersionId(), input.getVersionId(), actor, input.getReason().strip());
    }

    public PromptVersion active(String code) {
        PromptVersion version = mapper.active(code);
        if (version == null)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "提示词尚未发布，请联系管理员");
        return version;
    }

    public long startCall(PromptVersion version, String meetingId, Integer transcriptVersion, long actor, String requestKey, Integer outlineVersion) {
        PromptCall call = new PromptCall();
        call.setScenario(version.getScenario());
        call.setPromptVersionId(version.getId());
        call.setMeetingId(meetingId);
        call.setTranscriptVersion(transcriptVersion);
        call.setActorId(actor);
        call.setStatus(BusinessStatus.RUNNING.code());
        call.setRequestKey(requestKey);
        call.setOutlineVersion(outlineVersion);
        mapper.insertCall(call);
        return call.getId();
    }

    public void finishCall(long id, Integer status, long duration, String error) {
        PromptCall call = new PromptCall();
        call.setId(id);
        call.setStatus(status);
        call.setDurationMs(duration);
        call.setErrorCategory(error);
        mapper.finishCall(call);
    }

    /**
     * 仅记录服务商返回的元数据；缺失字段保留为空，不以零代替未知值。
     */
    public void recordMetadata(long id, ChatResponse response) {
        if (response == null || response.getMetadata() == null) return;
        var metadata = response.getMetadata();
        var call = new PromptCall();
        call.setId(id);
        call.setProvider("DeepSeek");
        call.setModelName(emptyToNull(metadata.getModel()));
        call.setProviderRequestId(emptyToNull(metadata.getId()));
        var usage = metadata.getUsage();
        if (usage != null && !(usage instanceof EmptyUsage)) {
            call.setInputTokens(usage.getPromptTokens());
            call.setOutputTokens(usage.getCompletionTokens());
            if (usage.getNativeUsage() instanceof DeepSeekApi.Usage nativeUsage && nativeUsage.promptTokensDetails() != null)
                call.setCachedTokens(nativeUsage.promptTokensDetails().cachedTokens());
        }
        mapper.updateMetadata(call);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /**
     * 按实际发送顺序保存系统消息和用户消息，不写入应用日志。
     */
    public void recordInput(long callId, String systemContent, String userContent) {
        var content = new AiCallContent();
        content.setCallId(callId);
        content.setSystemContent(systemContent);
        content.setUserContent(userContent);
        mapper.insertContent(content);
    }

    /**
     * 保存未经业务格式处理的模型输出；未收到响应时保持为空。
     */
    public void recordOutput(long callId, String output) {
        mapper.updateOutput(callId, output);
    }

    /** 保存工具调用和返回快照，可能包含敏感会议内容。 */
    public void recordToolTrace(long callId, String trace) {
        mapper.updateToolTrace(callId, trace);
    }

    /**
     * 获取调用内容，权限由管理员接口校验。
     */
    public AiCallContent content(long callId) {
        var value = mapper.content(callId);
        if (value == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "调用内容不存在或历史调用未记录内容");
        return value;
    }

    public List<PromptCall> calls(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        return mapper.calls(size, (page - 1) * size);
    }
}
