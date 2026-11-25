package com.ylz.futuremade.oa.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.knowledge.model.dto.*;
import com.ylz.futuremade.knowledge.service.MeetingResearchService;
import com.ylz.futuremade.oa.model.dto.TrackingResearchInput;
import com.ylz.futuremade.oa.model.entity.OaTask;
import com.ylz.futuremade.prompt.enums.PromptScenarioCode;
import com.ylz.futuremade.prompt.service.PromptService;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** 待办单 Agent 研究，按固定会议范围执行并合并展示结果。 */
@Service
public class TrackingResearchService {
    private final MeetingResearchService researcher;
    private final PromptService prompts;
    private final ObjectMapper json;

    public TrackingResearchService(MeetingResearchService researcher, PromptService prompts, ObjectMapper json) {
        this.researcher = researcher;
        this.prompts = prompts;
        this.json = json;
    }

    /** 后台任务只包含一场会议；同步请求逐场研究，全部使用同一模板版本。 */
    public MeetingResearchAnswer research(OaTask task, List<KnowledgeMeetingSelection> scope,
                                         long actor, String username) throws Exception {
        PromptTemplate template = new PromptTemplate(
                prompts.active(PromptScenarioCode.TASK_TRACKING_RESEARCH_INPUT.code()).getContent());
        TrackingResearchInput input = new TrackingResearchInput();
        input.setObjective("追踪待办进展，区分明确关联、可能关联和缺少依据的情况，引用实际纪要来源");
        input.setTaskContent(task.getContent());
        Map<String, Object> variables = json.convertValue(input, new TypeReference<Map<String, Object>>() {});
        String question = template.render(variables);
        List<MeetingResearchAnswer> answers = new ArrayList<>();
        for (KnowledgeMeetingSelection meeting : scope) {
            MultiMeetingQuestion request = new MultiMeetingQuestion();
            request.setQuestion(question);
            request.setMeetings(List.of(meeting));
            answers.add(researcher.progressResearch(request, actor, username));
        }
        if (answers.size() == 1) return answers.get(0);
        MeetingResearchAnswer result = new MeetingResearchAnswer();
        result.setAnswer(String.join("\n\n", answers.stream().map(MeetingResearchAnswer::getAnswer).toList()));
        result.setMeetings(scope);
        result.setSources(answers.stream().flatMap(answer -> answer.getSources().stream()).toList());
        result.setTodos(answers.stream().flatMap(answer -> answer.getTodos().stream()).toList());
        result.setCandidates(answers.stream().flatMap(answer -> answer.getCandidates().stream()).toList());
        result.setMissingMeetings(answers.stream().flatMap(answer -> answer.getMissingMeetings().stream()).toList());
        return result;
    }
}
