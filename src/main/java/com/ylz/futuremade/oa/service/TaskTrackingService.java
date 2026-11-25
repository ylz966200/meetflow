package com.ylz.futuremade.oa.service;

import com.ylz.futuremade.knowledge.model.dto.KnowledgeSource;
import com.ylz.futuremade.knowledge.model.dto.MeetingResearchAnswer;
import com.ylz.futuremade.meeting.model.dto.MinutesSection;
import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.meeting.service.MinutesService;
import com.ylz.futuremade.meeting.service.MinutesStructure;
import com.ylz.futuremade.oa.mapper.OaTaskMapper;
import com.ylz.futuremade.oa.mapper.TaskTrackingMapper;
import com.ylz.futuremade.oa.model.dto.TaskTrackingEntry;
import com.ylz.futuremade.oa.model.dto.TaskTrackingLinkRequest;
import com.ylz.futuremade.oa.model.dto.TaskTrackingRequest;
import com.ylz.futuremade.oa.model.entity.OaTask;
import com.ylz.futuremade.oa.model.entity.TaskTrackingLink;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeMeetingSelection;

import java.util.HashSet;
import java.util.Set;
import java.util.ArrayList;

import org.springframework.beans.factory.annotation.Value;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * 待办进展研究及用户确认的会议来源。
 */
@Service
public class TaskTrackingService {
    private final MeetingService meetings;
    private final MinutesService minutes;
    private final OaTaskMapper tasks;
    private final TaskTrackingMapper links;
    private final TrackingResearchService researcher;
    @Value("${meetflow.knowledge.max-meetings:5}")
    private int maxMeetings = 5;

    public TaskTrackingService(MeetingService meetings, MinutesService minutes, OaTaskMapper tasks,
                               TaskTrackingMapper links, TrackingResearchService researcher) {
        this.meetings = meetings;
        this.minutes = minutes;
        this.tasks = tasks;
        this.links = links;
        this.researcher = researcher;
    }

    /**
     * 仅在用户指定的会议中寻找可能的进展，不修改待办状态。
     */
    public MeetingResearchAnswer search(String meetingId, String taskId, TaskTrackingRequest request, long actor, String username) throws Exception {
        OaTask task = task(meetingId, taskId, actor);
        if (request.getMeetings() == null || request.getMeetings().isEmpty() || request.getMeetings().size() > maxMeetings)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择 1 至 " + maxMeetings + " 场后续会议");
        // 所有会议先通过权限校验，避免执行一半才发现范围中包含未授权会议。
        List<KnowledgeMeetingSelection> selected = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (KnowledgeMeetingSelection item : request.getMeetings()) {
            if (item == null || item.getMeetingId() == null || item.getMeetingId().equals(meetingId) || !ids.add(item.getMeetingId()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择不同于来源会议且不重复的会议");
            MinutesView view = minutes.get(item.getMeetingId(), actor, null);
            if (item.getMinutesVersion() != null && !item.getMinutesVersion().equals(view.getVersion()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "所选纪要版本已变化");
            KnowledgeMeetingSelection fixed = new KnowledgeMeetingSelection();
            fixed.setMeetingId(item.getMeetingId());
            fixed.setMinutesVersion(view.getVersion());
            selected.add(fixed);
        }
        MeetingResearchAnswer result = researcher.research(task, selected, actor, username);
        // 子研究耗时期间权限和版本可能变化，返回整个报告前再次检查全部范围。
        task(meetingId, taskId, actor);
        for (KnowledgeMeetingSelection item : selected) {
            if (!item.getMinutesVersion().equals(minutes.get(item.getMeetingId(), actor, null).getVersion()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "研究期间纪要已变化，请重新研究");
        }
        return result;
    }

    /**
     * 保存用户确认的关联，来源内容始终从固定版本读取。
     */
    public TaskTrackingEntry confirm(String meetingId, String taskId, TaskTrackingLinkRequest request, long actor) {
        OaTask task = task(meetingId, taskId, actor);
        if (!Long.valueOf(actor).equals(task.getAssigneeId())) meetings.requireOutlineEditor(meetingId, actor, false);
        KnowledgeSource source = source(request.getSourceMeetingId(), request.getMinutesVersion(), request.getSectionIndex(), actor);
        TaskTrackingLink link = new TaskTrackingLink();
        link.setId(UUID.randomUUID().toString());
        link.setTaskId(taskId);
        link.setSourceMeetingId(request.getSourceMeetingId());
        link.setMinutesVersion(request.getMinutesVersion());
        link.setSectionIndex(request.getSectionIndex());
        link.setCreatedBy(actor);
        try {
            links.insert(link);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该来源已确认关联", ex);
        }
        TaskTrackingEntry entry = new TaskTrackingEntry();
        entry.setLink(link);
        entry.setSource(source);
        return entry;
    }

    /**
     * 逐条重新检查来源会议权限，避免已撤销权限的内容泄露。
     */
    public List<TaskTrackingEntry> list(String meetingId, String taskId, long actor) {
        task(meetingId, taskId, actor);
        List<TaskTrackingEntry> result = new ArrayList<>();
        for (TaskTrackingLink link : links.list(taskId)) {
            TaskTrackingEntry entry = new TaskTrackingEntry();
            entry.setLink(link);
            entry.setSource(source(link.getSourceMeetingId(), link.getMinutesVersion(), link.getSectionIndex(), actor));
            result.add(entry);
        }
        return result;
    }

    /**
     * 撤销错误关联，由负责人或会议编辑者操作，重复撤销保持幂等。
     */
    public void remove(String meetingId, String taskId, String linkId, long actor) {
        OaTask task = task(meetingId, taskId, actor);
        if (!Long.valueOf(actor).equals(task.getAssigneeId())) meetings.requireOutlineEditor(meetingId, actor, false);
        links.delete(taskId, linkId);
    }

    private OaTask task(String meetingId, String taskId, long actor) {
        meetings.members(meetingId, actor);
        OaTask task = tasks.find(meetingId, taskId);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待办不存在");
        return task;
    }

    private KnowledgeSource source(String meetingId, Integer version, Integer index, long actor) {
        if (meetingId == null || meetingId.isBlank() || version == null || version <= 0 || index == null || index < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源无效");
        MinutesView view = minutes.get(meetingId, actor, version);
        if (view.getStructured() == null || index >= view.getStructured().getSections().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源纪要条目不存在");
        MinutesSection section = view.getStructured().getSections().get(index);
        KnowledgeSource source = new KnowledgeSource();
        source.setMeetingId(meetingId);
        source.setMinutesVersion(version);
        source.setSectionIndex(index);
        source.setTitle(section.getTitle());
        source.setContent(MinutesStructure.sectionContent(section));
        source.setMeetingTitle(meetings.title(meetingId, actor));
        return source;
    }
}
