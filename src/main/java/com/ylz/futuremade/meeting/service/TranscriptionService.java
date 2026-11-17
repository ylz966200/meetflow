package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.mapper.TranscriptionTaskMapper;
import com.ylz.futuremade.meeting.mapper.RecordingMapper;
import com.ylz.futuremade.meeting.model.dto.SubmitTranscriptionRequest;
import com.ylz.futuremade.meeting.model.dto.CompletedTranscriptionView;
import com.ylz.futuremade.meeting.model.dto.TranscriptionHistoryView;
import com.ylz.futuremade.meeting.model.dto.SpeakerAttribution;
import com.ylz.futuremade.meeting.model.dto.SpeakerReview;
import com.ylz.futuremade.meeting.model.dto.AssembledTranscriptions;
import com.ylz.futuremade.meeting.model.dto.AssembleTranscriptionsRequest;
import com.ylz.futuremade.meeting.model.dto.TranscriptionSource;
import com.ylz.futuremade.meeting.model.dto.TranscriptionTaskView;
import com.ylz.futuremade.meeting.model.entity.TranscriptionTaskModel;
import com.ylz.futuremade.meeting.transcript.TranscriptSegment;
import com.ylz.futuremade.meeting.transcript.SpeechRequestProperties;
import com.ylz.futuremade.meeting.transcript.VolcTranscriptionClient;
import com.ylz.futuremade.meeting.transcript.TranscriptChunker;
import com.ylz.futuremade.meeting.transcript.MeetingContextProperties;
import com.ylz.futuremade.meeting.transcript.MeetingContextTools;
import com.ylz.futuremade.meeting.transcript.ChunkExtractionContext;
import com.ylz.futuremade.meeting.model.dto.TranscriptChunkView;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;
import java.util.Set;
import java.util.HashSet;
import java.util.stream.Collectors;

/**
 * 提交和查询火山转写任务，识别结果需人工确认后才进入文字稿。
 */
@Service
public class TranscriptionService {
    /**
     * 已完成转写列表每页数量。
     */
    private static final int COMPLETED_PAGE_SIZE = 20;
    private final MeetingService access;
    private final TranscriptionTaskMapper tasks;
    private final ObjectMapper json;
    private final VolcTranscriptionClient client;
    private RecordingMapper recordings;
    private TranscriptChunker chunker;
    private MeetingContextProperties contextProperties;
    /**
     * 单个任务的自动查询次数上限。
     */
    @Value("${meetflow.speech.poll.max-attempts:240}")
    private int maxPollAttempts = 240;

    @Autowired
    public TranscriptionService(MeetingService access, TranscriptionTaskMapper tasks, ObjectMapper json,
                                @Value("${meetflow.speech.api-key:}") String apiKey, @Value("${meetflow.speech.resource-id:volc.bigasr.auc}") String resourceId,
                                SpeechRequestProperties speechProperties, RecordingMapper recordings, TranscriptChunker chunker,
                                MeetingContextProperties contextProperties) {
        this.access = access;
        this.tasks = tasks;
        this.json = json;
        this.recordings = recordings;
        this.chunker = chunker;
        this.contextProperties = contextProperties;
        this.client = apiKey.isBlank() ? null : new VolcTranscriptionClient(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(), json, URI.create("https://openspeech.bytedance.com"), apiKey, resourceId, speechProperties);
    }

    TranscriptionService(MeetingService access, TranscriptionTaskMapper tasks, ObjectMapper json, VolcTranscriptionClient client) {
        this.access = access;
        this.tasks = tasks;
        this.json = json;
        this.client = client;
    }

    public void requireConfigured() {
        if (client == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "未配置火山语音 API Key");
    }

    public TranscriptionTaskView submit(String meetingId, long actor, SubmitTranscriptionRequest input) {
        access.requireOutlineEditor(meetingId, actor, false);
        requireConfigured();
        URI url;
        try {
            if (input.getAudioUrl() == null || input.getAudioUrl().length() > 2048)
                throw new IllegalArgumentException();
            url = URI.create(input.getAudioUrl().strip());
            if (!"https".equalsIgnoreCase(url.getScheme()) || url.getHost() == null || url.getUserInfo() != null)
                throw new IllegalArgumentException();
            if (input.getFormat() == null || !List.of("wav", "mp3", "ogg").contains(input.getFormat()))
                throw new IllegalArgumentException();
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供 HTTPS 音频链接和 wav、mp3 或 ogg 格式");
        }
        var task = new TranscriptionTaskModel();
        task.setId(UUID.randomUUID().toString());
        task.setMeetingId(meetingId);
        task.setCreatedBy(actor);
        task.setStatus(BusinessStatus.SUBMITTING.code());
        tasks.insert(task);
        return submitPrepared(task.getId(), meetingId, url, input.getFormat());
    }

    /**
     * 原子创建关联任务，进程中断后调度器仍可按任务 ID 查询。
     */
    @Transactional
    public String prepareRecordingTask(String meetingId, String recordingId, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        requireConfigured();
        String taskId = UUID.randomUUID().toString();
        if (recordings.claimNewTask(meetingId, recordingId, taskId) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "录音正在识别或状态待确认，请勿重复提交");
        var task = new TranscriptionTaskModel();
        task.setId(taskId);
        task.setMeetingId(meetingId);
        task.setRecordingId(recordingId);
        task.setCreatedBy(actor);
        task.setStatus(BusinessStatus.SUBMITTING.code());
        tasks.insert(task);
        recordings.update(recordingId, BusinessStatus.SUBMITTED.code(), taskId);
        return taskId;
    }

    /**
     * 向火山提交已持久化的任务；超时保留不确定状态供后续查询。
     */
    public TranscriptionTaskView submitPrepared(String taskId, String meetingId, URI url, String format) {
        try {
            client.submit(UUID.fromString(taskId), url, format);
            tasks.update(taskId, BusinessStatus.PROCESSING.code(), null);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            tasks.update(taskId, BusinessStatus.UNKNOWN.code(), null);
        } catch (Exception ex) {
            com.ylz.futuremade.common.logging.ErrorDiagnostics.error("语音转写任务提交失败", ex);
            tasks.update(taskId, BusinessStatus.UNKNOWN.code(), null);
        }
        return view(tasks.find(meetingId, taskId));
    }

    public TranscriptionTaskView latest(String meetingId, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        var task = tasks.latest(meetingId);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无转写任务");
        return view(task);
    }

    /**
     * 仅允许会议组织者和编辑者读取本会议的成功转写记录。
     */
    public List<CompletedTranscriptionView> completed(String meetingId, long actor, int page) {
        access.requireOutlineEditor(meetingId, actor, false);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码不能小于零");
        return tasks.completed(meetingId, BusinessStatus.SUCCEEDED.code(), COMPLETED_PAGE_SIZE, (long) page * COMPLETED_PAGE_SIZE);
    }

    /**
     * 按会议和录音隔离转写历史。
     */
    public List<TranscriptionHistoryView> history(String meetingId, String recordingId, long actor, int page) {
        access.requireOutlineEditor(meetingId, actor, false);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码不能小于零");
        if (recordings.find(meetingId, recordingId) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "录音不存在");
        return tasks.history(meetingId, recordingId, COMPLETED_PAGE_SIZE, (long) page * COMPLETED_PAGE_SIZE);
    }

    /**
     * 查询本会议旧链接任务及无法回填录音关联的历史任务。
     */
    public List<TranscriptionHistoryView> unlinked(String meetingId, long actor, int page) {
        access.requireOutlineEditor(meetingId, actor, false);
        if (page < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "页码不能小于零");
        return tasks.unlinked(meetingId, COMPLETED_PAGE_SIZE, (long) page * COMPLETED_PAGE_SIZE);
    }

    public TranscriptionTaskView get(String meetingId, String taskId, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        var task = tasks.find(meetingId, taskId);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "转写任务不存在");
        return view(task);
    }

    /**
     * 获取指定任务的说话人校对快照；每次识别各自独立。
     */
    public SpeakerReview speakerReview(String meetingId, String taskId, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        var task = requireCompletedTask(meetingId, taskId);
        return reviewView(task);
    }

    /**
     * 预览单次转写的技术分段与相邻上文；不调用 AI、不写数据库。
     */
    @Transactional(readOnly = true)
    public List<TranscriptChunkView> previewChunks(String meetingId, String taskId, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        var task = requireCompletedTask(meetingId, taskId);
        return chunker.split(taskId, view(task).getSegments(), reviewView(task));
    }

    /**
     * 为指定分段创建只读上下文工具，数据和权限由服务端限定，不由模型传入。
     */
    @Transactional(readOnly = true)
    public MeetingContextTools contextTools(String meetingId, String taskId, long actor, int chunkIndex) {
        return extractionContext(meetingId, taskId, actor, chunkIndex).getTools();
    }

    /**
     * 将分段和工具绑定到同一次数据库读取的快照，避免校对版本不一致。
     */
    @Transactional(readOnly = true)
    public ChunkExtractionContext extractionContext(String meetingId, String taskId, long actor, int chunkIndex) {
        List<TranscriptChunkView> chunks = previewChunks(meetingId, taskId, actor);
        if (chunkIndex < 1 || chunkIndex > chunks.size())
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分段不存在");
        var current = chunks.get(chunkIndex - 1);
        var snapshot = chunks.stream().flatMap(chunk -> chunk.getUtterances().stream()).toList();
        int startIndex = current.getUtterances().get(0).getSegmentIndex();
        return new ChunkExtractionContext(current, new MeetingContextTools(snapshot, startIndex, contextProperties));
    }

    /**
     * 生成多条识别结果的合并预览，不写入会议文字稿或修改原识别内容。
     */
    @Transactional(readOnly = true)
    public AssembledTranscriptions assemble(String meetingId, long actor, AssembleTranscriptionsRequest request) {
        access.requireOutlineEditor(meetingId, actor, false);
        if (request == null || request.getTaskIds() == null || request.getTaskIds().isEmpty() || request.getTaskIds().size() > 20)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 至 20 条转写结果");
        var seenTasks = new HashSet<String>();
        var seenRecordings = new HashSet<String>();
        var sources = new ArrayList<TranscriptionSource>();
        var content = new StringBuilder();
        for (String taskId : request.getTaskIds()) {
            if (taskId == null || taskId.isBlank() || !seenTasks.add(taskId))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "转写任务不能为空或重复");
            var task = requireCompletedTask(meetingId, taskId);
            if (task.getRecordingId() != null && !seenRecordings.add(task.getRecordingId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "同一录音只能选择一个识别结果");
            var segments = view(task).getSegments();
            if (segments.isEmpty()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "所选任务没有识别内容");
            var review = reviewView(task);
            if (review.getAssignments().size() != segments.size())
                throw new IllegalStateException("校对结果与识别片段不一致");
            if (!content.isEmpty()) content.append('\n');
            for (int i = 0; i < segments.size(); i++) {
                var segment = segments.get(i);
                var assignment = review.getAssignments().get(i);
                String name = assignment.getMemberUsername() != null ? assignment.getMemberUsername() : assignment.getExternalName();
                if (name == null || name.isBlank())
                    name = "录音 " + (sources.size() + 1) + ("unknown".equals(segment.speakerLabel()) ? " 的未知说话人" : " 的说话人 " + segment.speakerLabel());
                content.append(name).append('：').append(segment.text()).append('\n');
                if (content.length() > 200000)
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "合并内容超过 20 万字符，请减少录音数量");
            }
            var source = new TranscriptionSource();
            source.setTaskId(taskId);
            source.setRecordingId(task.getRecordingId());
            source.setSpeakerReviewVersion(review.getVersion());
            sources.add(source);
        }
        var result = new AssembledTranscriptions();
        result.setContent(content.toString());
        result.setSources(sources);
        return result;
    }

    /**
     * 根据片段顺序保存说话人归属，验证会议成员并检查版本冲突。
     */
    @Transactional
    public SpeakerReview saveSpeakerReview(String meetingId, String taskId, long actor, SpeakerReview request) {
        access.requireOutlineEditor(meetingId, actor, true);
        var task = requireCompletedTask(meetingId, taskId);
        if (request == null || request.getVersion() == null || request.getVersion() < 0 || request.getAssignments() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "校对版本或说话人信息无效");
        int count = view(task).getSegments().size();
        if (request.getAssignments().size() != count)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "校对片段数量与识别结果不一致");
        Set<String> members = access.members(meetingId, actor).stream().map(member -> member.username()).collect(Collectors.toSet());
        for (SpeakerAttribution assignment : request.getAssignments()) {
            if (assignment == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "说话人信息不能为空");
            String username = assignment.getMemberUsername() == null ? "" : assignment.getMemberUsername().strip();
            String name = assignment.getExternalName() == null ? "" : assignment.getExternalName().strip();
            if ((!username.isEmpty() && !name.isEmpty()) || (!username.isEmpty() && !members.contains(username))
                    || name.length() > 100 || name.chars().anyMatch(Character::isISOControl))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "说话人必须是当前会议成员或有效的外部姓名");
            assignment.setMemberUsername(username.isEmpty() ? null : username);
            assignment.setExternalName(name.isEmpty() ? null : name);
        }
        try {
            if (tasks.updateSpeakerReview(meetingId, taskId, json.writeValueAsString(request.getAssignments()), request.getVersion()) != 1)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "说话人校对已更新，请重新加载");
            return reviewView(tasks.find(meetingId, taskId));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("说话人校对结果无法保存", ex);
        }
    }

    private TranscriptionTaskModel requireCompletedTask(String meetingId, String taskId) {
        var task = tasks.find(meetingId, taskId);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "转写任务不存在");
        if (!BusinessStatus.SUCCEEDED.code().equals(task.getStatus()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "仅成功的转写任务可校对说话人");
        return task;
    }

    private SpeakerReview reviewView(TranscriptionTaskModel task) {
        try {
            var review = new SpeakerReview();
            review.setVersion(task.getSpeakerReviewVersion() == null ? 0 : task.getSpeakerReviewVersion());
            if (task.getSpeakerReviewJson() != null) {
                review.setAssignments(json.readValue(task.getSpeakerReviewJson(), new TypeReference<List<SpeakerAttribution>>() {
                }));
            } else {
                var assignments = new ArrayList<SpeakerAttribution>();
                for (int i = 0; i < view(task).getSegments().size(); i++) assignments.add(new SpeakerAttribution());
                review.setAssignments(assignments);
            }
            return review;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("说话人校对结果无法读取", ex);
        }
    }

    public TranscriptionTaskView query(String meetingId, String taskId, long actor) {
        access.requireOutlineEditor(meetingId, actor, false);
        var task = tasks.find(meetingId, taskId);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "转写任务不存在");
        return queryTask(task);
    }

    /**
     * 定时任务使用已持久化的任务，无需依赖发起人的当前登录状态。
     */
    public void poll(TranscriptionTaskModel task) {
        queryTask(task);
    }

    private TranscriptionTaskView queryTask(TranscriptionTaskModel task) {
        String meetingId = task.getMeetingId(), taskId = task.getId();
        if (BusinessStatus.SUCCEEDED.code().equals(task.getStatus()) || BusinessStatus.FAILED.code().equals(task.getStatus()))
            return view(task);
        if (client == null) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "未配置火山语音 API Key");
        try {
            var result = client.query(UUID.fromString(taskId));
            if (result.state() == VolcTranscriptionClient.State.SUCCEEDED)
                tasks.complete(taskId, BusinessStatus.SUCCEEDED.code(), json.writeValueAsString(result.segments()), result.rawResponse());
            else tasks.update(taskId, BusinessStatus.PROCESSING.code(), null);
            return view(tasks.find(meetingId, taskId));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "查询转写结果中断，请稍后重试");
        } catch (Exception ex) {
            com.ylz.futuremade.common.logging.ErrorDiagnostics.error("查询语音转写结果失败", ex);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "查询转写结果失败，请检查资源配置并稍后重试");
        }
    }

    private TranscriptionTaskView view(TranscriptionTaskModel task) {
        try {
            var view = new TranscriptionTaskView();
            view.setId(task.getId());
            view.setStatus(task.getStatus());
            view.setCreatedAt(task.getCreatedAt());
            view.setUpdatedAt(task.getUpdatedAt());
            view.setPollingStopped(task.getPollCount() != null && task.getPollCount() >= maxPollAttempts);
            view.setSegments(task.getSegmentsJson() == null ? List.of() : json.readValue(task.getSegmentsJson(), new TypeReference<List<TranscriptSegment>>() {
            }));
            return view;
        } catch (Exception ex) {
            throw new IllegalStateException("转写结果无法读取", ex);
        }
    }
}
