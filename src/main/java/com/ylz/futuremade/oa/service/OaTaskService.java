package com.ylz.futuremade.oa.service;

import com.ylz.futuremade.identity.mapper.AccountMapper;
import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.service.MeetingService;
import com.ylz.futuremade.oa.enums.OaTaskStatus;
import com.ylz.futuremade.oa.mapper.OaTaskMapper;
import com.ylz.futuremade.oa.model.dto.OaActionPreview;
import com.ylz.futuremade.oa.model.dto.OaTaskCreateRequest;
import com.ylz.futuremade.oa.model.dto.OaTaskStatusRequest;
import com.ylz.futuremade.oa.model.dto.OaTaskView;
import com.ylz.futuremade.oa.model.entity.OaTask;
import com.ylz.futuremade.oa.model.entity.OaTaskAudit;
import java.util.List;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** 已确认会议行动项的本地 OA 待办创建与跟进。 */
@Service
public class OaTaskService {
    private final MeetingService meetings;
    private final OaActionPreviewService previews;
    private final AccountMapper accounts;
    private final OaTaskMapper tasks;
    private final OaTaskSourceService sources;

    public OaTaskService(MeetingService meetings, OaActionPreviewService previews, AccountMapper accounts, OaTaskMapper tasks, OaTaskSourceService sources) {
        this.meetings = meetings;
        this.previews = previews;
        this.accounts = accounts;
        this.tasks = tasks;
        this.sources = sources;
    }

    /** 短事务内锁定会议并校验最新纪要，重复创建由数据库唯一约束阻止。 */
    @Transactional
    public OaTask create(String meetingId, long actor, OaTaskCreateRequest request) {
        meetings.requireOutlineEditor(meetingId, actor, true);
        OaActionPreview preview = previews.preview(meetingId, actor);
        if (request.getMinutesVersion() == null || !request.getMinutesVersion().equals(preview.getMinutesVersion()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "纪要版本已变化，请刷新后重新确认");
        Integer index = request.getActionIndex();
        if (index == null || index < 0 || index >= preview.getActions().size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行动项无效，请刷新页面");
        if (request.getContent() == null || request.getContent().isBlank() || request.getContent().length() > 5000 || request.getDueDate() == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请填写待办内容和明确的截止日期");
        if (request.getAssigneeUsername() == null || request.getAssigneeUsername().isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择会议成员作为负责人");
        AccountMapper.Account account = accounts.findByUsername(request.getAssigneeUsername().strip());
        if (account == null || !account.enabled())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责人不存在或已停用");
        List<MeetingMapper.Member> members = meetings.members(meetingId, actor);
        boolean member = members.stream().anyMatch(item -> item.username().equals(account.username()) && item.enabled());
        if (!member) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "负责人必须是有效的会议成员");
        OaTask task = new OaTask();
        task.setId(UUID.randomUUID().toString());
        task.setMeetingId(meetingId);
        task.setMinutesVersion(preview.getMinutesVersion());
        task.setActionIndex(index);
        task.setContent(request.getContent().strip());
        task.setAssigneeId(account.id());
        task.setDueDate(request.getDueDate());
        task.setStatus(OaTaskStatus.PENDING.code());
        task.setCreatedBy(actor);
        try {
            tasks.insert(task);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "该纪要行动项已经创建待办，请刷新查看", exception);
        }
        audit(task.getId(), actor, null, task.getStatus());
        return tasks.find(meetingId, task.getId());
    }

    /** 会议成员可以查看本会议的待办，包括历史纪要版本。 */
    public List<OaTaskView> list(String meetingId, long actor) {
        meetings.members(meetingId, actor);
        List<OaTaskView> result = tasks.list(meetingId);
        sources.enrich(meetingId, result);
        return result;
    }

    /** 负责人或会议编辑者可以按合法状态流转待办。 */
    @Transactional
    public OaTask changeStatus(String meetingId, String taskId, long actor, OaTaskStatusRequest request) {
        meetings.members(meetingId, actor);
        OaTask task = tasks.find(meetingId, taskId);
        if (task == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待办不存在");
        if (!Long.valueOf(actor).equals(task.getAssigneeId())) meetings.requireOutlineEditor(meetingId, actor, false);
        Integer expected = request.getExpectedStatus();
        Integer next = request.getStatus();
        if (expected == null || next == null || !allowed(expected, next))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "不支持的待办状态变更");
        if (tasks.changeStatus(meetingId, taskId, expected, next) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "待办状态已变化，请刷新后重试");
        audit(taskId, actor, expected, next);
        return tasks.find(meetingId, taskId);
    }

    /** 会议成员可查看本会议待办的状态历史。 */
    public List<OaTaskAudit> history(String meetingId, String taskId, long actor) {
        meetings.members(meetingId, actor);
        if (tasks.find(meetingId, taskId) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "待办不存在");
        return tasks.history(taskId);
    }

    private void audit(String taskId, long actor, Integer previousStatus, int status) {
        OaTaskAudit audit = new OaTaskAudit();
        audit.setId(UUID.randomUUID().toString());
        audit.setTaskId(taskId);
        audit.setActorId(actor);
        audit.setPreviousStatus(previousStatus);
        audit.setStatus(status);
        tasks.audit(audit);
    }

    private boolean allowed(int current, int next) {
        if (current == OaTaskStatus.PENDING.code())
            return next == OaTaskStatus.IN_PROGRESS.code() || next == OaTaskStatus.COMPLETED.code() || next == OaTaskStatus.CANCELLED.code();
        if (current == OaTaskStatus.IN_PROGRESS.code())
            return next == OaTaskStatus.COMPLETED.code() || next == OaTaskStatus.CANCELLED.code();
        return current == OaTaskStatus.COMPLETED.code() && next == OaTaskStatus.IN_PROGRESS.code();
    }
}
