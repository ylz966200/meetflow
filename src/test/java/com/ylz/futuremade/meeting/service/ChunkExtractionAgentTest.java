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
import com.ylz.futuremade.assistant.service.ReactAgentExecutor;
import com.ylz.futuremade.assistant.service.ModelOutputTruncatedException;
import com.ylz.futuremade.assistant.model.dto.AiCallContext;
import com.ylz.futuremade.prompt.model.entity.PromptVersion;
import com.ylz.futuremade.meeting.model.dto.ChunkExtractionView;
import com.ylz.futuremade.assistant.tool.AuthorizedToolRegistry;
import org.springframework.ai.support.ToolCallbacks;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChunkExtractionAgentTest {
    private com.ylz.futuremade.assistant.model.dto.AiCallContext audit() {
        PromptVersion version = new com.ylz.futuremade.prompt.model.entity.PromptVersion();
        version.setContent("提示词");
        AiCallContext context = new com.ylz.futuremade.assistant.model.dto.AiCallContext(version, null, null, 1L, null, null);
        context.setLastCallId(42L);
        return context;
    }

    private ChunkUtterance utterance(int index, String text) {
        ChunkUtterance u=new ChunkUtterance();u.setTaskId("task");u.setSegmentIndex(index);u.setSpeaker("成员");u.setText(text);return u;
    }
    private ChunkExtractionContext context(MeetingContextProperties properties) {
        ChunkUtterance earlier=utterance(0,"预算十万");ChunkUtterance current=utterance(1,"这个预算偏高");
        TranscriptChunkView chunk=new TranscriptChunkView();chunk.setTaskId("task");chunk.setChunkIndex(2);chunk.setSpeakerReviewVersion(1);chunk.setUtterances(List.of(current));chunk.setContextBefore(List.of());
        return new ChunkExtractionContext(chunk,new MeetingContextTools(List.of(earlier,current),1,properties));
    }
    private ChatResponse response(AssistantMessage message) { return new ChatResponse(List.of(new Generation(message))); }

    private org.mockito.stubbing.Answer<ChatResponse> responses(ChatResponse... responses) {
        java.util.concurrent.atomic.AtomicInteger round = new java.util.concurrent.atomic.AtomicInteger();
        return invocation -> {
            com.ylz.futuremade.assistant.model.dto.AiCallContext audit = invocation.getArgument(0);
            audit.setLastCallId(42L);
            return responses[round.getAndIncrement()];
        };
    }

    @Test void callsScopedToolAndAcceptsOnlyReturnedEvidence() throws Exception {
        AiModelGateway model=mock(AiModelGateway.class);AuthorizedToolRegistry tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); PromptService prompts=mock(PromptService.class);MeetingContextProperties properties=new MeetingContextProperties();
        AssistantMessage.ToolCall toolCall=new AssistantMessage.ToolCall("call-1","function","search_meeting_context","{\"query\":\"预算\"}");
        AssistantMessage toolMessage=AssistantMessage.builder().content("").toolCalls(List.of(toolCall)).build();
        AssistantMessage finalMessage=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"OBJECTION\",\"content\":\"预算偏高\",\"evidenceIndices\":[0,1],\"needsReview\":false}]}");
        when(model.call(any(),any(Prompt.class))).thenAnswer(responses(response(toolMessage),response(finalMessage)));
        ChunkExtractionAgent agent=new ChunkExtractionAgent(new ReactAgentExecutor(model,prompts,new ObjectMapper()),tools,new ObjectMapper(),properties);
        ChunkExtractionView result=agent.extract(context(properties),audit(),"user");
        assertEquals(List.of(0,1),result.getItems().get(0).getEvidenceIndices());assertEquals("task",result.getTaskId());
        verify(prompts).recordToolTrace(eq(42L),contains("search_meeting_context"));
        verify(model,times(2)).call(any(),any(Prompt.class));
    }

    @Test void rejectsInventedEvidenceOrEvidenceOnlyFromContext() {
        AiModelGateway model=mock(AiModelGateway.class);AuthorizedToolRegistry tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); PromptService prompts=mock(PromptService.class);MeetingContextProperties properties=new MeetingContextProperties();
        AssistantMessage output=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"FACT\",\"content\":\"不存在\",\"evidenceIndices\":[999],\"needsReview\":false}]}");
        when(model.call(any(),any(Prompt.class))).thenAnswer(responses(response(output)));
        ChunkExtractionAgent agent=new ChunkExtractionAgent(new ReactAgentExecutor(model,prompts,new ObjectMapper()),tools,new ObjectMapper(),properties);
        assertThrows(IllegalArgumentException.class,()->agent.extract(context(properties),audit(),"user"));
    }

    @Test void continuesCallingToolsAfterWarningThreshold() throws Exception {
        AiModelGateway model=mock(AiModelGateway.class);AuthorizedToolRegistry tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); PromptService prompts=mock(PromptService.class);MeetingContextProperties properties=new MeetingContextProperties();properties.setMaxCalls(1);
        List<AssistantMessage.ToolCall> calls=List.of(new AssistantMessage.ToolCall("c1","function","search_meeting_context","{\"query\":\"预算\"}"),new AssistantMessage.ToolCall("c2","function","search_meeting_context","{\"query\":\"预算\"}"));
        AssistantMessage finalMessage=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"OBJECTION\",\"content\":\"预算偏高\",\"evidenceIndices\":[0,1],\"needsReview\":true}]}");
        when(model.call(any(),any(Prompt.class))).thenAnswer(responses(response(AssistantMessage.builder().content("").toolCalls(calls).build()),response(finalMessage)));
        ChunkExtractionAgent agent=new ChunkExtractionAgent(new ReactAgentExecutor(model,prompts,new ObjectMapper()),tools,new ObjectMapper(),properties);
        assertTrue(agent.extract(context(properties),audit(),"user").getItems().get(0).getNeedsReview());
        org.mockito.ArgumentCaptor<Prompt> captured=org.mockito.ArgumentCaptor.forClass(Prompt.class);verify(model,times(2)).call(any(),captured.capture());
        Prompt followup=captured.getAllValues().get(1);
        assertNotNull(followup);
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
        ReactAgentExecutor executor = new ReactAgentExecutor(model, prompts, new ObjectMapper());
        MeetingContextTools allowed = context(new MeetingContextProperties()).getTools();
        Exception failure = assertThrows(Exception.class,
                () -> executor.execute("outline_chunk_extraction", audit(), "提取会议议题", ToolCallbacks.from(allowed)));
        assertTrue(failure.toString().contains("模型请求了未授权的工具"));
        verifyNoInteractions(prompts);
        verify(model).call(any(), any(Prompt.class));
    }

    @Test void refusesToExtractOnlyFromContext() {
        AiModelGateway model=mock(AiModelGateway.class);AuthorizedToolRegistry tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); PromptService prompts=mock(PromptService.class);MeetingContextProperties properties=new MeetingContextProperties();
        ChunkUtterance previous=utterance(0,"预算十万");ChunkUtterance current=utterance(1,"继续讨论");
        TranscriptChunkView chunk=new TranscriptChunkView();chunk.setTaskId("task");chunk.setChunkIndex(2);chunk.setSpeakerReviewVersion(0);chunk.setUtterances(List.of(current));chunk.setContextBefore(List.of(previous));
        AssistantMessage output=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"FACT\",\"content\":\"预算十万\",\"evidenceIndices\":[0],\"needsReview\":false}]}");
        when(model.call(any(),any(Prompt.class))).thenAnswer(responses(response(output)));
        ChunkExtractionContext context=new ChunkExtractionContext(chunk,new MeetingContextTools(List.of(previous,current),1,properties));
        assertThrows(IllegalArgumentException.class,()->new ChunkExtractionAgent(new ReactAgentExecutor(model,prompts,new ObjectMapper()),tools,new ObjectMapper(),properties).extract(context,audit(),"user"));
    }
    @Test
    void acceptsEmptyExtractionWithoutToolCalls() throws Exception {
        AiModelGateway model = mock(AiModelGateway.class);
        AuthorizedToolRegistry tools = mock(AuthorizedToolRegistry.class);
        when(tools.forUserScoped(anyString(), any())).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        when(model.call(any(), any(Prompt.class))).thenAnswer(responses(response(new AssistantMessage("{\"items\":[]}"))));
        MeetingContextProperties properties = new MeetingContextProperties();
        ChunkExtractionAgent agent = new ChunkExtractionAgent(new ReactAgentExecutor(model, mock(PromptService.class), new ObjectMapper()), tools, new ObjectMapper(), properties);
        assertTrue(agent.extract(context(properties), audit(), "user").getItems().isEmpty());
    }

    @Test
    void preservesTruncationForOuterSplitAndRetry() throws Exception {
        AiModelGateway model = mock(AiModelGateway.class);
        AuthorizedToolRegistry tools = mock(AuthorizedToolRegistry.class);
        when(tools.forUserScoped(anyString(), any())).thenReturn(new org.springframework.ai.tool.ToolCallback[0]);
        when(model.call(any(), any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(
                new AssistantMessage("{\"items\":"),
                org.springframework.ai.chat.metadata.ChatGenerationMetadata.builder().finishReason("length").build()))));
        MeetingContextProperties properties = new MeetingContextProperties();
        ChunkExtractionAgent agent = new ChunkExtractionAgent(new ReactAgentExecutor(model, mock(PromptService.class), new ObjectMapper()), tools, new ObjectMapper(), properties);
        assertThrows(ModelOutputTruncatedException.class, () -> agent.extract(context(properties), audit(), "user"));
    }

    @Test
    void retriesMalformedToolArgumentsWithoutTreatingFeedbackAsEvidence() throws Exception {
        AiModelGateway model = mock(AiModelGateway.class);
        AuthorizedToolRegistry tools = mock(AuthorizedToolRegistry.class);
        when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1)));
        PromptService prompts = mock(PromptService.class);
        AssistantMessage invalid = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("bad", "function", "search_meeting_context", "{\"query\":{}}"))).build();
        AssistantMessage valid = AssistantMessage.builder().content("").toolCalls(List.of(
                new AssistantMessage.ToolCall("good", "function", "search_meeting_context", "{\"query\":\"预算\"}"))).build();
        AssistantMessage output = new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"OBJECTION\",\"content\":\"预算偏高\",\"evidenceIndices\":[0,1],\"needsReview\":false}]}");
        when(model.call(any(), any(Prompt.class))).thenAnswer(responses(response(invalid), response(valid), response(output)));
        MeetingContextProperties properties = new MeetingContextProperties();
        ChunkExtractionAgent agent = new ChunkExtractionAgent(new ReactAgentExecutor(model, prompts, new ObjectMapper()), tools, new ObjectMapper(), properties);
        assertEquals(List.of(0, 1), agent.extract(context(properties), audit(), "user").getItems().get(0).getEvidenceIndices());
        verify(prompts, times(2)).recordToolTrace(eq(42L), anyString());
        verify(model, times(3)).call(any(), any(Prompt.class));
    }

}
