package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.mapper.RecordingMapper;
import com.ylz.futuremade.meeting.model.dto.SubmitTranscriptionRequest;
import com.ylz.futuremade.meeting.model.dto.TranscriptionTaskView;
import com.ylz.futuremade.meeting.model.entity.RecordingModel;
import com.ylz.futuremade.meeting.storage.AudioObjectStorage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RecordingUploadServiceTest {
    @Test void deletesObjectAndPreservesTranscriptionHistory() {
        var access=mock(MeetingService.class);var mapper=mock(RecordingMapper.class);var storage=mock(AudioObjectStorage.class);
        var service=new RecordingUploadService(access,mapper,storage,mock(TranscriptionService.class));
        var record=new RecordingModel();record.setStatus(BusinessStatus.SUBMITTED.code());record.setObjectKey("object-key");
        when(mapper.find("meeting","recording")).thenReturn(record);when(mapper.claimDeletion("meeting","recording")).thenReturn(1);
        service.delete("meeting","recording",1);
        verify(access).requireOutlineEditor("meeting",1,false);verify(storage).delete("object-key");verify(mapper).deleteClaimed("meeting","recording");
    }
    @Test void doesNotDeleteAnActiveTranscription() {
        var mapper=mock(RecordingMapper.class);var storage=mock(AudioObjectStorage.class);
        var service=new RecordingUploadService(mock(MeetingService.class),mapper,storage,mock(TranscriptionService.class));
        var record=new RecordingModel();record.setStatus(BusinessStatus.SUBMITTED.code());
        when(mapper.find("meeting","recording")).thenReturn(record);when(mapper.claimDeletion("meeting","recording")).thenReturn(0);
        assertThrows(ResponseStatusException.class,()->service.delete("meeting","recording",1));verify(storage,never()).delete(anyString());
    }
    @Test void uploadAndTranscriptionAreSeparateActions() {
        var access=mock(MeetingService.class);var mapper=mock(RecordingMapper.class);var storage=mock(AudioObjectStorage.class);var speech=mock(TranscriptionService.class);
        var service=new RecordingUploadService(access,mapper,storage,speech);
        when(storage.signedGetUrl(anyString())).thenReturn("https://example.com/audio.wav?signature=secret");
        var stored=new RecordingModel[1];
        when(mapper.insert(any())).thenAnswer(i->{stored[0]=i.getArgument(0);return 1;});
        when(mapper.find(anyString(),anyString())).thenAnswer(i->stored[0]);
        when(mapper.claim(anyString(),anyString())).thenReturn(1);
        var result=new TranscriptionTaskView();result.setId("task-id");result.setStatus(BusinessStatus.PROCESSING.code());
        when(speech.prepareRecordingTask(eq("meeting"),anyString(),eq(1L))).thenReturn("task-id");
        when(speech.submitPrepared(eq("task-id"),eq("meeting"),any(),eq("wav"))).thenReturn(result);
        var actual=service.upload("meeting",1,new MockMultipartFile("file","meeting.wav","audio/wav",new byte[]{1,2,3}));
        assertNotNull(actual.getId());verify(speech,never()).prepareRecordingTask(anyString(),anyString(),anyLong());verify(storage,never()).signedGetUrl(anyString());
        var capture=org.mockito.ArgumentCaptor.forClass(RecordingModel.class);verify(mapper).insert(capture.capture());
        assertFalse(capture.getValue().getObjectKey().contains("secret"));
        var task=service.transcribe("meeting",actual.getId(),1);assertEquals("task-id",task.getId());
        verify(speech).prepareRecordingTask("meeting",actual.getId(),1);verify(access,atLeastOnce()).requireOutlineEditor("meeting",1,false);
    }
    @Test void rejectsUnsupportedFilesBeforeUploading() {
        var storage=mock(AudioObjectStorage.class);var service=new RecordingUploadService(mock(MeetingService.class),mock(RecordingMapper.class),storage,mock(TranscriptionService.class));
        assertThrows(ResponseStatusException.class,()->service.upload("meeting",1,new MockMultipartFile("file","invalid.txt","text/plain",new byte[]{1})));
        verify(storage,never()).upload(anyString(),any(),anyLong());
    }
    @Test void rejectsConcurrentSubmissionWithoutCallingSpeech() {
        var mapper=mock(RecordingMapper.class);var speech=mock(TranscriptionService.class);
        var service=new RecordingUploadService(mock(MeetingService.class),mapper,mock(AudioObjectStorage.class),speech);
        var recording=new RecordingModel();recording.setId("recording");recording.setStatus(BusinessStatus.UPLOADED.code());recording.setFormat("wav");
        when(mapper.find("meeting","recording")).thenReturn(recording);when(speech.prepareRecordingTask("meeting","recording",1)).thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT));
        assertThrows(ResponseStatusException.class,()->service.transcribe("meeting","recording",1));
        verify(speech,never()).submit(anyString(),anyLong(),any());
    }
}
