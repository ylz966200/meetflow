package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import com.ylz.futuremade.assistant.service.ToolCallingExecutor;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeMeetingSelection;
import com.ylz.futuremade.knowledge.model.dto.MeetingResearchAnswer;
import com.ylz.futuremade.knowledge.model.dto.MultiMeetingQuestion;
import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.meeting.service.MinutesService;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.prompt.service.PromptService;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.ylz.futuremade.assistant.tool.AuthorizedToolRegistry;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 对用户选定的会议执行只读、多步的知识研究。
 */
@Service
public class MeetingResearchService {
    private final MultiMeetingKnowledgeAccessService access;
    private final MinutesService minutes;
    private final PromptService prompts;
    private final ToolCallingExecutor executor;
    private final int maxMeetings;
    private final AuthorizedToolRegistry registry;

    public MeetingResearchService(MultiMeetingKnowledgeAccessService access, MinutesService minutes,
                                  PromptService prompts, ToolCallingExecutor executor, AuthorizedToolRegistry registry,
                                  @Value("${meetflow.knowledge.max-meetings:5}") int maxMeetings) {
        this.access = access;
        this.minutes = minutes;
        this.prompts = prompts;
        this.executor = executor;
        this.registry = registry;
        this.maxMeetings = maxMeetings;
    }

    /**
     * 固定会议版本，执行研究工具并在返回前重新检查权限和版本。
     */
    public MeetingResearchAnswer research(MultiMeetingQuestion request, long actor, String username) throws Exception {
        if (request.getQuestion() == null || request.getQuestion().isBlank() || request.getQuestion().length() > 1000)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "问题不能为空且不能超过1000字符");
        if (request.getMeetings() == null || request.getMeetings().isEmpty() || request.getMeetings().size() > maxMeetings)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 至 " + maxMeetings + " 场会议");
        List<KnowledgeMeetingSelection> selected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (KnowledgeMeetingSelection item : request.getMeetings()) {
            if (item == null || item.getMeetingId() == null || item.getMeetingId().isBlank() || item.getMeetingId().contains(",") || !ids.add(item.getMeetingId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会议编号无效或重复");
            MinutesView view = minutes.get(item.getMeetingId(), actor, null);
            if (item.getMinutesVersion() != null && !item.getMinutesVersion().equals(view.getVersion()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "会议纪要版本已变化");
            KnowledgeMeetingSelection fixed = new KnowledgeMeetingSelection();
            fixed.setMeetingId(item.getMeetingId());
            fixed.setMinutesVersion(view.getVersion());
            selected.add(fixed);
        }
        if (String.join(",", ids).length() > 1024)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "会议范围过长");
        PromptVersion prompt = prompts.active(PromptScenarioCode.MEETING_RESEARCH.code());
        String requestId = UUID.randomUUID().toString();
        AiCallContext audit = new AiCallContext(prompt, selected.get(0).getMeetingId(), null, actor, requestId, null);
        audit.setMeetingIds(String.join(",", ids.stream().sorted().toList()));
        MeetingResearchTools tools = new MeetingResearchTools(access, actor, selected);
        ToolCallback[] authorized = registry.forUserScoped(username, tools);
        if (authorized.length != 2) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "缺少会议研究工具权限，请联系管理员授权");
        }
        String answer = executor.execute(audit, request.getQuestion(), authorized, 4, (call, result) -> {
        });
        if (answer == null || answer.isBlank() || tools.sources().isEmpty())
            throw new IllegalStateException("研究未取得可验证的会议来源");
        for (KnowledgeMeetingSelection item : selected) {
            MinutesView current = minutes.get(item.getMeetingId(), actor, null);
            if (!item.getMinutesVersion().equals(current.getVersion()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "会议纪要版本已变化，请重新研究");
        }
        MeetingResearchAnswer result = new MeetingResearchAnswer();
        result.setTodos(tools.todos());
        result.setAnswer(answer.strip());
        result.setMeetings(selected);
        result.setSources(tools.sources());
        result.setMissingMeetings(tools.missingMeetings());
        result.setRequestKey(requestId);
        return result;
    }
}
