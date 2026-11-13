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
        when(model.call(any(Prompt.class))).thenReturn(response(toolMessage),response(finalMessage));
        var agent=new ChunkExtractionAgent(new ToolCallingExecutor(model),tools,prompts,new ObjectMapper(),properties);
        var result=agent.extract(context(properties),"提示词",42,"user");
        assertEquals(List.of(0,1),result.getItems().get(0).getEvidenceIndices());assertEquals("task",result.getTaskId());
        verify(prompts).recordInput(eq(42L),eq("提示词"),anyString());
        verify(prompts).recordToolTrace(eq(42L),contains("search_meeting_context"));
        verify(prompts).recordOutput(eq(42L),anyString());verify(model,times(2)).call(any(Prompt.class));
    }

    @Test void rejectsInventedEvidenceOrEvidenceOnlyFromContext() {
        var model=mock(AiModelGateway.class);var tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); var prompts=mock(PromptService.class);var properties=new MeetingContextProperties();
        var output=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"FACT\",\"content\":\"不存在\",\"evidenceIndices\":[999],\"needsReview\":false}]}");
        when(model.call(any(Prompt.class))).thenReturn(response(output));
        var agent=new ChunkExtractionAgent(new ToolCallingExecutor(model),tools,prompts,new ObjectMapper(),properties);
        assertThrows(IllegalArgumentException.class,()->agent.extract(context(properties),"提示词",42,"user"));
    }

    @Test void stopsWhenModelRequestsMoreToolsThanAllowed() {
        var model=mock(AiModelGateway.class);var tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); var prompts=mock(PromptService.class);var properties=new MeetingContextProperties();properties.setMaxCalls(1);
        var calls=List.of(new AssistantMessage.ToolCall("c1","function","search_meeting_context","{\"query\":\"预算\"}"),new AssistantMessage.ToolCall("c2","function","search_meeting_context","{\"query\":\"预算\"}"));
        when(model.call(any(Prompt.class))).thenReturn(response(AssistantMessage.builder().content("").toolCalls(calls).build()));
        var agent=new ChunkExtractionAgent(new ToolCallingExecutor(model),tools,prompts,new ObjectMapper(),properties);
        assertThrows(IllegalStateException.class,()->agent.extract(context(properties),"提示词",42,"user"));
        verify(model,times(1)).call(any(Prompt.class));
    }

    @Test void refusesToExtractOnlyFromContext() {
        var model=mock(AiModelGateway.class);var tools=mock(AuthorizedToolRegistry.class); when(tools.forUserScoped(anyString(), any())).thenAnswer(inv -> ToolCallbacks.from((Object) inv.getArgument(1))); var prompts=mock(PromptService.class);var properties=new MeetingContextProperties();
        var previous=utterance(0,"预算十万");var current=utterance(1,"继续讨论");
        var chunk=new TranscriptChunkView();chunk.setTaskId("task");chunk.setChunkIndex(2);chunk.setSpeakerReviewVersion(0);chunk.setUtterances(List.of(current));chunk.setContextBefore(List.of(previous));
        var output=new AssistantMessage("{\"items\":[{\"topic\":\"预算\",\"type\":\"FACT\",\"content\":\"预算十万\",\"evidenceIndices\":[0],\"needsReview\":false}]}");
        when(model.call(any(Prompt.class))).thenReturn(response(output));
        var context=new ChunkExtractionContext(chunk,new MeetingContextTools(List.of(previous,current),1,properties));
        assertThrows(IllegalArgumentException.class,()->new ChunkExtractionAgent(new ToolCallingExecutor(model),tools,prompts,new ObjectMapper(),properties).extract(context,"提示词",1,"user"));
    }
}
