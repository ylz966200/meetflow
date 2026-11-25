package com.ylz.futuremade.knowledge.service;

import com.ylz.futuremade.knowledge.enums.ResearchTodoStatus;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeMeetingSelection;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeSource;
import com.ylz.futuremade.knowledge.model.dto.MultiMeetingAnswer;
import com.ylz.futuremade.knowledge.model.dto.MultiMeetingQuestion;
import com.ylz.futuremade.knowledge.model.dto.ResearchTodo;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/**
 * 绑定用户及选定会议范围的只读研究工具，每次执行由知识库服务校验权限和版本。
 */
public class MeetingResearchTools {
    private final MultiMeetingKnowledgeAccessService access;
    private final long actor;
    private final List<KnowledgeMeetingSelection> meetings;
    private final List<KnowledgeSource> sources = new ArrayList<>();
    private final List<String> missingMeetings = new ArrayList<>();
    private List<ResearchTodo> todos = List.of();

    public MeetingResearchTools(MultiMeetingKnowledgeAccessService access, long actor, List<KnowledgeMeetingSelection> meetings) {
        this.access = access;
        this.actor = actor;
        this.meetings = meetings;
    }

    /**
     * 在用户选定的会议中研究一个子问题，返回答案及可追溯的纪要来源。
     */
    @Tool(name = "research_meeting_knowledge", description = "在用户选定且有权限的会议知识库中检索并回答一个子问题，返回参考纪要。可为不同子问题重复调用；资料不足时应说明。")
    public MultiMeetingAnswer research(@ToolParam(description = "需要研究的具体问题，最多1000字") String question) throws Exception {
        MultiMeetingQuestion input = new MultiMeetingQuestion();
        input.setQuestion(question);
        input.setMeetings(meetings);
        input.setConversationId(UUID.randomUUID().toString());
        input.setBypassCache(true);
        MultiMeetingAnswer result = access.answer(input, actor);
        sources.addAll(result.getSources());
        if (result.getMissingMeetings() != null) missingMeetings.addAll(result.getMissingMeetings());
        return result;
    }

    /**
     * 接收助手当前的研究计划，供最终结果展示。
     */
    @Tool(name = "update_research_todos", description = "更新本次研究的完整步骤列表。每项包含 content 和 status，status 为 1 待处理、2 进行中、3 已完成。根据实际工具结果更新，不得虚报完成。")
    public String updateTodos(@ToolParam(description = "完整的研究步骤列表") List<ResearchTodo> items) {
        if (items == null || items.isEmpty() || items.size() > 12) {
            return "请提供 1 至 12 项研究步骤。";
        }
        for (ResearchTodo item : items) {
            if (item == null || item.getContent() == null || item.getContent().isBlank()
                    || item.getContent().length() > 200 || item.getStatus() == null
                    || !ResearchTodoStatus.supports(item.getStatus())) {
                return "步骤内容或状态无效，请修正后重新提交。";
            }
        }
        todos = List.copyOf(items);
        return "研究步骤已更新。";
    }

    /**
     * 返回助手最后一次提交的步骤。
     */
    public List<ResearchTodo> todos() {
        return todos;
    }

    /**
     * 已经由知识库返回的来源，供研究结果展示。
     */
    public List<KnowledgeSource> sources() {
        return List.copyOf(sources);
    }

    /**
     * 未检索到结果的会议名称。
     */
    public List<String> missingMeetings() {
        return missingMeetings.stream().distinct().toList();
    }
}
