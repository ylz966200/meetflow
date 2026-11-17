package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;

import com.ylz.futuremade.meeting.mapper.RecordingMapper;
import com.ylz.futuremade.meeting.model.dto.SubmitTranscriptionRequest;
import com.ylz.futuremade.meeting.model.dto.TranscriptionTaskView;
import com.ylz.futuremade.meeting.model.dto.RecordingView;
import com.ylz.futuremade.meeting.model.entity.RecordingModel;
import com.ylz.futuremade.meeting.storage.AudioObjectStorage;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.net.URI;
import java.util.List;

/**
 * 管理会议录音的上传和手动转写提交。
 */
@Service
public class RecordingUploadService {
    private static final long MAX_BYTES = 100L * 1024 * 1024;
    private final MeetingService access;
    private final RecordingMapper records;
    private final AudioObjectStorage storage;
    private final TranscriptionService transcriptions;

    public RecordingUploadService(MeetingService access, RecordingMapper records, AudioObjectStorage storage, TranscriptionService transcriptions) {
        this.access = access;
        this.records = records;
        this.storage = storage;
        this.transcriptions = transcriptions;
    }

    public RecordingView upload(String meetingId, long actor, MultipartFile file) {
        access.requireOutlineEditor(meetingId, actor, false);
        storage.requireConfigured();
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "录音大小须在 1 字节至 100 MB 之间");
        String name = file.getOriginalFilename();
        if (name == null || name.isBlank() || name.length() > 255 || name.contains("/") || name.contains("\\") || name.chars().anyMatch(Character::isISOControl))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "录音文件名无效");
        int dot = name.lastIndexOf('.');
        String format = dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!Set.of("wav", "mp3", "ogg").contains(format))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "只支持 wav、mp3、ogg 格式");
        String id = UUID.randomUUID().toString();
        var record = new RecordingModel();
        record.setId(id);
        record.setMeetingId(meetingId);
        record.setObjectKey("meetings/" + meetingId + "/recordings/" + id + "." + format);
        record.setOriginalName(name);
        record.setFileSize(file.getSize());
        record.setFormat(format);
        record.setStatus(BusinessStatus.UPLOADING.code());
        record.setCreatedBy(actor);
        records.insert(record);
        try (var stream = file.getInputStream()) {
            storage.upload(record.getObjectKey(), stream, file.getSize());
            records.update(id, BusinessStatus.UPLOADED.code(), null);
        } catch (Exception ex) {
            com.ylz.futuremade.common.logging.ErrorDiagnostics.error("会议录音上传失败", ex);
            records.update(id, BusinessStatus.FAILED.code(), null);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "录音上传失败，请检查 TOS 配置");
        }
        return view(records.find(meetingId, id));
    }

    public RecordingView latest(String meetingId, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        var record = records.latest(meetingId);
        if (record == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无上传录音");
        return view(record);
    }

    /** 分页展示会议录音及最近一次任务状态，保留已删除录音的历史入口。 */
    public List<RecordingView> list(String meetingId, long actor, int page) {
        access.requireOutlineEditor(meetingId, actor, false);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码不能小于零");
        return records.list(meetingId, 20, (long) page * 20).stream().map(this::view).toList();
    }

    /** 删除录音存储对象，保留转写、录音元数据和会议内容。 */
    public void delete(String meetingId, String id, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        var record = records.find(meetingId, id);
        if (record == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "录音不存在");
        storage.requireConfigured();
        if (!BusinessStatus.DELETING.code().equals(record.getStatus()) && records.claimDeletion(meetingId, id) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "录音正在上传或转写，请完成后再删除");
        storage.delete(record.getObjectKey());
        records.deleteClaimed(meetingId, id);
    }

    public TranscriptionTaskView transcribe(String meetingId, String id, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        transcriptions.requireConfigured();
        storage.requireConfigured();
        var record = records.find(meetingId, id);
        if (record == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "录音不存在");
        String url = storage.signedGetUrl(record.getObjectKey());
        String taskId = transcriptions.prepareRecordingTask(meetingId, id, actor);
        return transcriptions.submitPrepared(taskId, meetingId, URI.create(url), record.getFormat());
    }

    private RecordingView view(RecordingModel record) {
        var value = new RecordingView();
        value.setId(record.getId());
        value.setOriginalName(record.getOriginalName());
        value.setFileSize(record.getFileSize());
        value.setFormat(record.getFormat());
        value.setStatus(record.getStatus());
        value.setTranscriptionTaskId(record.getTranscriptionTaskId());
        value.setTranscriptionStatus(record.getTranscriptionStatus());
        value.setCreatedAt(record.getCreatedAt());
        return value;
    }
}
