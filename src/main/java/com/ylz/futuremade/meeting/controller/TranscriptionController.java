package com.ylz.futuremade.meeting.controller;

import com.ylz.futuremade.meeting.model.dto.SubmitTranscriptionRequest;
import com.ylz.futuremade.meeting.model.dto.CompletedTranscriptionView;
import com.ylz.futuremade.meeting.model.dto.TranscriptionHistoryView;
import com.ylz.futuremade.meeting.model.dto.SpeakerReview;
import com.ylz.futuremade.meeting.model.dto.TranscriptChunkView;
import com.ylz.futuremade.meeting.model.dto.ChunkExtractionRequest;
import com.ylz.futuremade.meeting.model.dto.ChunkExtractionView;
import com.ylz.futuremade.meeting.service.ChunkExtractionService;
import com.ylz.futuremade.meeting.model.dto.AssembledTranscriptions;
import com.ylz.futuremade.meeting.model.dto.AssembleTranscriptionsRequest;
import com.ylz.futuremade.meeting.model.dto.OrganizeTranscriptRequest;
import com.ylz.futuremade.meeting.model.dto.OrganizedTranscriptView;
import com.ylz.futuremade.meeting.model.dto.TranscriptionErrorView;
import com.ylz.futuremade.meeting.service.TranscriptAiDraftService;
import com.ylz.futuremade.meeting.model.dto.TranscriptionTaskView;
import com.ylz.futuremade.meeting.model.dto.RecordingView;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.TranscriptionService;
import com.ylz.futuremade.meeting.service.RecordingUploadService;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

/**
 * 录音转写控制器：组织者和编辑者可提交并查询会议录音识别任务。
 */
@RestController
@RequestMapping("/api/meetings/{id}/transcriptions")
public class TranscriptionController {
    private final MeetingService meetings;
    private final TranscriptionService transcriptions;
    private final RecordingUploadService recordings;
    private final TranscriptAiDraftService transcriptDrafts;
    private final ChunkExtractionService chunkExtractions;

    /**
     * 返回此控制器主动抛出的业务错误，不包含内部异常和敏感内容。
     */
    @ExceptionHandler(org.springframework.web.server.ResponseStatusException.class)
    public org.springframework.http.ResponseEntity<TranscriptionErrorView> businessError(org.springframework.web.server.ResponseStatusException ex) {
        var error = new TranscriptionErrorView();
        error.setStatus(ex.getStatusCode().value());
        error.setMessage(ex.getReason() == null ? "请求处理失败" : ex.getReason());
        return org.springframework.http.ResponseEntity.status(ex.getStatusCode()).body(error);
    }

    public TranscriptionController(MeetingService meetings, TranscriptionService transcriptions, RecordingUploadService recordings, TranscriptAiDraftService transcriptDrafts, ChunkExtractionService chunkExtractions) {
        this.meetings = meetings;
        this.transcriptions = transcriptions;
        this.recordings = recordings;
        this.transcriptDrafts = transcriptDrafts;
        this.chunkExtractions = chunkExtractions;
    }

    /**
     * 上传本地录音至私有 TOS，不自动发起转写。
     */
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    public RecordingView upload(@PathVariable String id, @RequestParam("file") MultipartFile file, Principal principal) {
        return recordings.upload(id, meetings.userId(principal.getName()), file);
    }

    /**
     * 获取会议最近一条可用录音，供用户选择转写。
     */
    @GetMapping("/recordings/latest")
    public RecordingView latestRecording(@PathVariable String id, Principal principal) {
        return recordings.latest(id, meetings.userId(principal.getName()));
    }

    /**
     * 分页查看录音及最近任务状态，已删除的录音保留历史入口；需组织者或编辑者权限。
     */
    @GetMapping("/recordings")
    public List<RecordingView> listRecordings(@PathVariable String id, @RequestParam(defaultValue = "0") int page, Principal principal) {
        return recordings.list(id, meetings.userId(principal.getName()), page);
    }

    /**
     * 分页查看当前会议某条录音的转写历史，需组织者或编辑者权限。
     */
    @GetMapping("/recordings/{recordingId}/history")
    public List<TranscriptionHistoryView> history(@PathVariable String id, @PathVariable String recordingId, @RequestParam(defaultValue = "0") int page, Principal principal) {
        return transcriptions.history(id, recordingId, meetings.userId(principal.getName()), page);
    }

    /**
     * 查询当前会议中未关联录音的历史任务，需组织者或编辑者权限。
     */
    @GetMapping("/unlinked")
    public List<TranscriptionHistoryView> unlinked(@PathVariable String id, @RequestParam(defaultValue = "0") int page, Principal principal) {
        return transcriptions.unlinked(id, meetings.userId(principal.getName()), page);
    }

    /**
     * 合并所选成功转写的校对结果，返回预览，不保存文字稿。
     */
    @PostMapping("/assemble")
    public AssembledTranscriptions assemble(@PathVariable String id, @RequestBody AssembleTranscriptionsRequest request, Principal principal) {
        return transcriptions.assemble(id, meetings.userId(principal.getName()), request);
    }

    /**
     * 使用版本化提示词整理所选转写，结果需人工确认；请求标识用于避免重复扣费。
     */
    @PostMapping("/organize")
    public OrganizedTranscriptView organize(@PathVariable String id, @RequestBody OrganizeTranscriptRequest request, @RequestHeader("Idempotency-Key") String key, Principal principal) {
        return transcriptDrafts.generate(id, meetings.userId(principal.getName()), key, request);
    }

    /**
     * 删除会议录音及存储对象，保留转写结果；处理中录音不可删除。
     */
    @DeleteMapping("/recordings/{recordingId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void deleteRecording(@PathVariable String id, @PathVariable String recordingId, Principal principal) {
        recordings.delete(id, recordingId, meetings.userId(principal.getName()));
    }

    /**
     * 手动提交已上传的录音；同一录音并发提交时只允许一个请求。
     */
    @PostMapping("/recordings/{recordingId}/submit")
    public TranscriptionTaskView submitRecording(@PathVariable String id, @PathVariable String recordingId, Principal principal) {
        return recordings.transcribe(id, recordingId, meetings.userId(principal.getName()));
    }

    /**
     * 提交 HTTPS 音频链接；超时等不确定情况返回任务状态供查询，避免盲目重提。
     */
    @PostMapping
    public TranscriptionTaskView submit(@PathVariable String id, @RequestBody SubmitTranscriptionRequest request, Principal principal) {
        return transcriptions.submit(id, meetings.userId(principal.getName()), request);
    }

    /**
     * 查询会议最近一条转写任务。
     */
    @GetMapping("/latest")
    public TranscriptionTaskView latest(@PathVariable String id, Principal principal) {
        return transcriptions.latest(id, meetings.userId(principal.getName()));
    }

    /**
     * 分页获取本会议成功的转写记录，供组织者或编辑者选择。
     */
    @GetMapping("/completed")
    public List<CompletedTranscriptionView> completed(@PathVariable String id, @RequestParam(defaultValue = "0") int page, Principal principal) {
        return transcriptions.completed(id, meetings.userId(principal.getName()), page);
    }

    /**
     * 读取会议指定转写任务的当前已保存状态，不触发火山查询。
     */
    @GetMapping("/{taskId}")
    public TranscriptionTaskView get(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return transcriptions.get(id, taskId, meetings.userId(principal.getName()));
    }

    /**
     * 获取成功转写任务的说话人校对快照，仅组织者和编辑者可访问。
     */
    @GetMapping("/{taskId}/speaker-review")
    public SpeakerReview speakerReview(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return transcriptions.speakerReview(id, taskId, meetings.userId(principal.getName()));
    }

    /**
     * 预览成功转写的有序技术分段及参考上文，仅组织者和编辑者可访问。
     */
    @GetMapping("/{taskId}/chunks/preview")
    public List<TranscriptChunkView> previewChunks(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return transcriptions.previewChunks(id, taskId, meetings.userId(principal.getName()));
    }

    /**
     * 提取一个分段的候选信息；同一请求标识重试不会重复发起模型调用。
     */
    @PostMapping("/{taskId}/chunks/{chunkIndex}/extract")
    public ChunkExtractionView extractChunk(@PathVariable String id, @PathVariable String taskId, @PathVariable int chunkIndex,
                                            @RequestBody ChunkExtractionRequest request, @RequestHeader("Idempotency-Key") String key, Principal principal) {
        return chunkExtractions.extract(id, taskId, chunkIndex, meetings.userId(principal.getName()), key, request);
    }

    /**
     * 保存逐句说话人校对结果，校验成员身份和快照版本。
     */
    @PutMapping("/{taskId}/speaker-review")
    public SpeakerReview saveSpeakerReview(@PathVariable String id, @PathVariable String taskId, @RequestBody SpeakerReview request, Principal principal) {
        return transcriptions.saveSpeakerReview(id, taskId, meetings.userId(principal.getName()), request);
    }

    /**
     * 主动向火山查询指定任务，已完成任务直接读取已保存的结果。
     */
    @PostMapping("/{taskId}/query")
    public TranscriptionTaskView query(@PathVariable String id, @PathVariable String taskId, Principal principal) {
        return transcriptions.query(id, taskId, meetings.userId(principal.getName()));
    }
}
