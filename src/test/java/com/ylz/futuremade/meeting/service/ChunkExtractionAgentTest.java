package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.model.dto.ChunkUtterance;
import com.ylz.futuremade.meeting.model.dto.TranscriptChunkView;
import com.ylz.futuremade.meeting.transcript.ChunkExtractionContext;
import com.ylz.futuremade.meeting.transcript.MeetingContextProperties;
import com.ylz.futuremade.meeting.transcript.MeetingContextTools;
import com.ylz.futuremade.prompt.service.PromptService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import com.ylz.futuremade.assistant.service.AiModelGateway;
import com.ylz.futuremade.assistant.service.ToolCallingExecutor;
import com.ylz.futuremade.assistant.tool.AuthorizedToolRegistry;
import org.springframework.ai.support.ToolCallbacks;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkExtractionAgentTest {
    private com.ylz.futuremade.assistant.model.dto.AiCallContext audit() {
        var version = new com.ylz.futuremade.prompt.model.entity.PromptVersion();
        version.setContent("提示词");
        var context = new com.ylz.futuremade.assistant.model.dto.AiCallContext(version, null, null, 1L, null, null);
        context.setLastCallId(42L);
        return context;
    }

    private ChunkUtterance utterance(int index, String text) {
        var u=new ChunkUtterance();u.setTaskId("task");u.setSegmentIndex(index);u.setSpeaker("成员");u.setText(text);return u;
    }
    private ChunkExtractionContext context(MeetingContextProperties properties) {
        var earlier=utterance(0,"预算十万");var current=utterance(1,"这个预算偏高");
        var chunk=new TranscriptChunkView();chunk.setTaskId("task");chunk.setChunkIndex(2);chunk.setSpeakerReviewVersion(1);chunk.setUtterances(List.of(current));chunk.setContextBefore(List.of());
        return new ChunkExtractionContext(chunk,new MeetingContextTools(List.of(earlier,current),1,properties));
    }
    private ChatResponse response(AssistantMessage message) { return new ChatResponse(List.of(new Generation(message))); }

    @Test void callsScopedToolAndAcceptsOnlyReturnedEvidence() throws Exception {
        var model=mock(AiModelGateway.class);var tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); var prompts=mock(PromptService.class);var properties=new MeetingContextProperties();
        var toolCall=new AssistantMessage.ToolCall("call-1","function","search_meeting_context","{\"query\":\"预算\"}");
        var toolMessage=AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
        var finalMessage=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"OBJECTION\",\"content\":\"预算偏高\",\"evidenceIndices\":[0,1],\"needsReview\":false}]}");
        when(model.call(any(),any(Prompt.class))).thenReturn(response(toolMessage),response(finalMessage));
        var agent=new ChunkExtractionAgent(new ToolCallingExecutor(model,prompts,new ObjectMapper()),tools,new ObjectMapper(),properties);
        var result=agent.extract(context(properties),audit(),"user");
        assertEquals(List.of(0,1),result.getItems().get(0).getEvidenceIndices());assertEquals("task",result.getTaskId());
        verify(prompts).recordToolTrace(eq(42L),contains("search_meeting_context"));
        verify(model,times(2)).call(any(),any(Prompt.class));
    }

    @Test void rejectsInventedEvidenceOrEvidenceOnlyFromContext() {
        var model=mock(AiModelGateway.class);var tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); var prompts=mock(PromptService.class);var properties=new MeetingContextProperties();
        var output=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"FACT\",\"content\":\"不存在\",\"evidenceIndices\":[999],\"needsReview\":false}]}");
        when(model.call(any(),any(Prompt.class))).thenReturn(response(output));
        var agent=new ChunkExtractionAgent(new ToolCallingExecutor(model,prompts,new ObjectMapper()),tools,new ObjectMapper(),properties);
        assertThrows(IllegalArgumentException.class,()->agent.extract(context(properties),audit(),"user"));
    }

    @Test void continuesCallingToolsAfterWarningThreshold() throws Exception {
        var model=mock(AiModelGateway.class);var tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); var prompts=mock(PromptService.class);var properties=new MeetingContextProperties();properties.setMaxCalls(1);
        var calls=List.of(new AssistantMessage.ToolCall("c1","function","search_meeting_context","{\"query\":\"预算\"}"),new AssistantMessage.ToolCall("c2","function","search_meeting_context","{\"query\":\"预算\"}"));
        var finalMessage=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"OBJECTION\",\"content\":\"预算偏高\",\"evidenceIndices\":[0,1],\"needsReview\":true}]}");
        when(model.call(any(),any(Prompt.class))).thenReturn(response(AssistantMessage.builder().content("").toolCalls(calls).build()),response(finalMessage));
        var agent=new ChunkExtractionAgent(new ToolCallingExecutor(model,prompts,new ObjectMapper()),tools,new ObjectMapper(),properties);
        assertTrue(agent.extract(context(properties),audit(),"user").getItems().get(0).getNeedsReview());
        var captured=org.mockito.ArgumentCaptor.forClass(Prompt.class);verify(model,times(2)).call(any(),captured.capture());
        var followup=captured.getAllValues().get(1);
        assertFalse(((org.springframework.ai.model.tool.ToolCallingChatOptions)followup.getOptions()).getToolCallbacks().isEmpty());
        verify(prompts,times(2)).recordToolTrace(eq(42L),anyString());
    }

    @Test
    void frameworkRejectsToolOutsideRequestScope() {
        AiModelGateway model = mock(AiModelGateway.class);
        PromptService prompts = mock(PromptService.class);
        AssistantMessage.ToolCall unauthorized = new AssistantMessage.ToolCall("unauthorized", "function",
                "currentWeather", "{\"location\":\"北京\"}");
        when(model.call(any(), any(Prompt.class))).thenReturn(response(
                AssistantMessage.builder().content("").toolCalls(List.of(unauthorized)).build()));
        ToolCallingExecutor executor = new ToolCallingExecutor(model, prompts, new ObjectMapper());
        MeetingContextTools allowed = context(new MeetingContextProperties()).getTools();
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> executor.execute(audit(), "提取会议议题", ToolCallbacks.from(allowed), 4,
                        (call, result) -> fail("未授权工具不应执行")));
        assertEquals("模型请求了未授权的工具", failure.getMessage());
        verifyNoInteractions(prompts);
        verify(model).call(any(), any(Prompt.class));
    }

    @Test void refusesToExtractOnlyFromContext() {
        var model=mock(AiModelGateway.class);var tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); var prompts=mock(PromptService.class);var properties=new MeetingContextProperties();
        var previous=utterance(0,"预算十万");var current=utterance(1,"继续讨论");
        var chunk=new TranscriptChunkView();chunk.setTaskId("task");chunk.setChunkIndex(2);chunk.setSpeakerReviewVersion(0);chunk.setUtterances(List.of(current));chunk.setContextBefore(List.of(previous));
        var output=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"FACT\",\"content\":\"预算十万\",\"evidenceIndices\":[0],\"needsReview\":false}]}");
        when(model.call(any(),any(Prompt.class))).thenReturn(response(output));
        var context=new ChunkExtractionContext(chunk,new MeetingContextTools(List.of(previous,current),1,properties));
        assertThrows(IllegalArgumentException.class,()->new ChunkExtractionAgent(new ToolCallingExecutor(model,prompts,new ObjectMapper()),tools,new ObjectMapper(),properties).extract(context,audit(),"user"));
    }
}
