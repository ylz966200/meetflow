package com.ylz.futuremade.meeting.service;
import com.ylz.futuremade.meeting.mapper.TranscriptionTaskMapper;
import com.ylz.futuremade.meeting.model.entity.TranscriptionTaskModel;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TranscriptionPollingSchedulerTest {
    @Test void claimedTasksAreQueriedAndReleasedEvenWhenOneFails() {
        var mapper=mock(TranscriptionTaskMapper.class);var service=mock(TranscriptionService.class);
        var first=new TranscriptionTaskModel();first.setId("task-1");var second=new TranscriptionTaskModel();second.setId("task-2");
        when(mapper.due(20,240)).thenReturn(List.of(first,second));when(mapper.claim(anyString(),anyString(),anyInt())).thenReturn(1);
        doThrow(new IllegalStateException("模拟错误")).when(service).poll(first);
        new TranscriptionPollingScheduler(mapper,service,true,60,240,20,120).poll();
        verify(service).poll(first);verify(service).poll(second);verify(mapper,times(2)).release(anyString(),anyString(),anyInt());
    }
    @Test void anotherInstanceClaimingTheTaskPreventsDuplicateQuery() {
        var mapper=mock(TranscriptionTaskMapper.class);var service=mock(TranscriptionService.class);
        var task=new TranscriptionTaskModel();task.setId("task-1");when(mapper.due(20,240)).thenReturn(List.of(task));when(mapper.claim(anyString(),anyString(),anyInt())).thenReturn(0);
        new TranscriptionPollingScheduler(mapper,service,true,60,240,20,120).poll();
        verify(service,never()).poll(any());verify(mapper,never()).release(anyString(),anyString(),anyInt());
    }
}
