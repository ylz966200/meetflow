package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.mapper.TranscriptionTaskMapper;
import com.ylz.futuremade.meeting.model.dto.SubmitTranscriptionRequest;
import com.ylz.futuremade.meeting.model.entity.TranscriptionTaskModel;
import com.ylz.futuremade.meeting.transcript.TranscriptSegment;
import com.ylz.futuremade.meeting.transcript.TranscriptionQueryResult;
import com.ylz.futuremade.meeting.transcript.VolcTranscriptionClient;
import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TranscriptionServiceTest {
    @Test void submittedTaskCanBeQueriedAndSegmentsArePersisted() throws Exception {
        var access=mock(MeetingService.class);var mapper=mock(TranscriptionTaskMapper.class);var client=mock(VolcTranscriptionClient.class);
        var service=new TranscriptionService(access,mapper,new ObjectMapper(),client);
        var request=new SubmitTranscriptionRequest();request.setAudioUrl("https://example.com/a.wav?signature=secret");request.setFormat("wav");
        var stored=new TranscriptionTaskModel[1];
        when(mapper.insert(any())).thenAnswer(i->{stored[0]=i.getArgument(0);return 1;});
        when(mapper.find(anyString(),anyString())).thenAnswer(i->stored[0]);
        when(mapper.update(anyString(),anyInt(),any())).thenAnswer(i->{stored[0].setStatus(i.getArgument(1));stored[0].setSegmentsJson(i.getArgument(2));return 1;});
        when(mapper.complete(anyString(),anyInt(),anyString(),anyString())).thenAnswer(i->{stored[0].setStatus(i.getArgument(1));stored[0].setSegmentsJson(i.getArgument(2));stored[0].setRawResponse(i.getArgument(3));return 1;});
        var submitted=service.submit("meeting",1,request);assertEquals(BusinessStatus.PROCESSING.code(),submitted.getStatus());
        verify(client).submit(eq(UUID.fromString(submitted.getId())),eq(URI.create(request.getAudioUrl())),eq("wav"));
        String raw="{\"result\":{\"extra\":\"原始字段\"}}";
        when(client.query(any())).thenReturn(new TranscriptionQueryResult(VolcTranscriptionClient.State.SUCCEEDED,List.of(new TranscriptSegment(0,100,"1","你好")),raw));
        var result=service.query("meeting",submitted.getId(),1);assertEquals(BusinessStatus.SUCCEEDED.code(),result.getStatus());assertEquals("1",result.getSegments().get(0).speakerLabel());
        assertEquals(raw,stored[0].getRawResponse());
        verify(mapper).complete(eq(submitted.getId()),eq(BusinessStatus.SUCCEEDED.code()),anyString(),eq(raw));
        verify(access,atLeastOnce()).requireOutlineEditor("meeting",1,false);
        service.query("meeting",submitted.getId(),1);verify(client,times(1)).query(any());
    }
    @Test void uncertainSubmitRetainsRequestIdForQuery() throws Exception {
        var mapper=mock(TranscriptionTaskMapper.class);var client=mock(VolcTranscriptionClient.class);var service=new TranscriptionService(mock(MeetingService.class),mapper,new ObjectMapper(),client);
        var task=new TranscriptionTaskModel[1];when(mapper.insert(any())).thenAnswer(i->{task[0]=i.getArgument(0);return 1;});when(mapper.find(anyString(),anyString())).thenAnswer(i->task[0]);
        when(mapper.update(anyString(),anyInt(),any())).thenAnswer(i->{task[0].setStatus(i.getArgument(1));return 1;});
        doThrow(new IOException("模拟超时")).when(client).submit(any(),any(),anyString());
        var request=new SubmitTranscriptionRequest();request.setAudioUrl("https://example.com/a.mp3");request.setFormat("mp3");
        var result=service.submit("meeting",1,request);assertEquals(BusinessStatus.UNKNOWN.code(),result.getStatus());assertNotNull(UUID.fromString(result.getId()));
    }
}
