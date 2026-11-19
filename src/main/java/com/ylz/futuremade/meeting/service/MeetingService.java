package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.mapper.TranscriptAiDraftMapper;
import com.ylz.futuremade.common.enums.BusinessStatus;

import com.ylz.futuremade.identity.mapper.AccountMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Service
public class MeetingService {
    private static final Logger log = LoggerFactory.getLogger(MeetingService.class);
    private final MeetingMapper meetings;
    private final AccountMapper accounts;
    private final TranscriptAiDraftMapper drafts;
    private final TranscriptSegmentStore segmentStore;

    public MeetingService(MeetingMapper meetings, AccountMapper accounts, TranscriptAiDraftMapper drafts, TranscriptSegmentStore segmentStore) {
        this.meetings = meetings;
        this.accounts = accounts;
        this.drafts = drafts;
        this.segmentStore = segmentStore;
    }

    public long userId(String username) {
        var account = accounts.findByUsername(username);
        if (account == null || !account.enabled()) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        return account.id();
    }

    /** 返回当前用户有权访问的会议名称。 */
    public String title(String meetingId, long actor) {
        var meeting = meetings.find(meetingId, actor);
        if (meeting == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权访问");
        return meeting.title();
    }

    public List<String> searchUsers(String meetingId, long actor, String query) {
        Long owner = meetings.owner(meetingId);
        if (owner == null || owner != actor)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权操作");
        if (query == null || query.isBlank() || query.length() > 100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请输入用户名");
        String prefix = query.strip().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return meetings.findEligibleUsers(meetingId, actor, prefix);
    }

    @Transactional(readOnly = true)
    public List<MeetingMapper.Member> members(String meetingId, long actor) {
        if (meetings.find(meetingId, actor) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权访问");
        return meetings.members(meetingId);
    }

    @Transactional
    public void addMember(String meetingId, long actor, String username, String role) {
        Long owner = meetings.lockOwner(meetingId);
        if (owner == null || owner != actor)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权操作");
        if (!"EDITOR".equals(role) && !"VIEWER".equals(role))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色应为 EDITOR 或 VIEWER");
        if (username == null || username.isBlank())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户名不能为空");
        var target = accounts.findByUsername(username.strip());
        if (target == null || !target.enabled())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "用户不存在或已禁用");
        if (target.id() == actor) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "组织者无需添加为成员");
        try {
            meetings.addMember(meetingId, target.id(), role);
            meetings.auditMember(meetingId, actor, target.id(), "ADD", null, role);
            log.debug("会议成员写入完成，等待事务提交，会议ID={}，操作者ID={}，成员ID={}，角色={}", meetingId, actor, target.id(), role);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "成员已存在");
        }
    }

    @Transactional
    public void updateMember(String meetingId, long actor, String username, String expectedRole, String newRole) {
        long target = authorizedTarget(meetingId, actor, username, expectedRole);
        if (!"EDITOR".equals(newRole) && !"VIEWER".equals(newRole))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "角色无效");
        if (expectedRole.equals(newRole)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请选择不同角色");
        if (meetings.changeMemberRole(meetingId, target, newRole, expectedRole) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "成员已变更，请刷新");
        meetings.auditMember(meetingId, actor, target, "ROLE_CHANGE", expectedRole, newRole);
    }

    @Transactional
    public void removeMember(String meetingId, long actor, String username, String expectedRole) {
        long target = authorizedTarget(meetingId, actor, username, expectedRole);
        if (meetings.removeMember(meetingId, target, expectedRole) != 1)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "成员已变更，请刷新");
        meetings.auditMember(meetingId, actor, target, "REMOVE", expectedRole, null);
    }

    private long authorizedTarget(String meetingId, long actor, String username, String expectedRole) {
        Long owner = meetings.lockOwner(meetingId);
        if (owner == null || owner != actor)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权操作");
        if (username == null || username.isBlank() || (!"EDITOR".equals(expectedRole) && !"VIEWER".equals(expectedRole)))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "成员或原角色无效");
        var target = accounts.findByUsername(username);
        if (target == null || target.id() == owner)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "成员不存在或不能操作组织者");
        return target.id();
    }

    public MeetingMapper.Transcript getTranscript(String meetingId, long actor, Integer version) {
        if (meetings.find(meetingId, actor) == null)
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权访问");
        var result = version == null ? meetings.latestTranscript(meetingId) : meetings.transcript(meetingId, version);
        if (result == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "文字稿不存在");
        return result;
    }

    @Transactional
    public MeetingMapper.Transcript saveTranscript(String meetingId, long actor, String content, Integer expectedVersion) {
        return saveTranscript(meetingId, actor, content, expectedVersion, null);
    }

    /**
     * 保存人工确认的文字稿，并可在同一事务中关联来源 AI 草稿。
     */
    @Transactional
    public MeetingMapper.Transcript saveTranscript(String meetingId, long actor, String content, Integer expectedVersion, String aiDraftId) {
        requireEditor(meetingId, actor, true);
        if (content == null || content.isBlank() || content.length() > 200000 || expectedVersion == null || expectedVersion < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文字稿或版本号无效");
        }
        int latest = meetings.latestVersion(meetingId);
        if (latest != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文字稿已更新，请重新加载");
        if (aiDraftId != null && !aiDraftId.isBlank()) {
            var draft = drafts.find(aiDraftId);
            if (draft == null || !meetingId.equals(draft.getMeetingId()) || !Long.valueOf(actor).equals(draft.getActorId()) || !BusinessStatus.SUCCEEDED.code().equals(draft.getStatus()) || !expectedVersion.equals(draft.getExpectedVersion()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "AI 草稿不存在、无权限或来源文字稿版本已变化");
        }
        if (latest > 0 && (aiDraftId == null || aiDraftId.isBlank())) {
            var previous = meetings.transcript(meetingId, latest);
            if (ContentComparison.normalize(content).equals(ContentComparison.normalize(previous.content())))
                return previous;
        }
        meetings.insertTranscript(meetingId, latest + 1, content, actor);
        segmentStore.saveVersion(meetingId, latest + 1, content);
        if (aiDraftId != null && !aiDraftId.isBlank()) meetings.attachTranscriptDraft(meetingId, latest + 1, aiDraftId);
        log.debug("文字稿版本写入完成，等待事务提交，会议ID={}，操作者ID={}，版本={}", meetingId, actor, latest + 1);
        return meetings.transcript(meetingId, latest + 1);
    }

    private void requireEditor(String meetingId, long actor, boolean lock) {
        Long owner = lock ? meetings.lockOwner(meetingId) : meetings.owner(meetingId);
        if (owner == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权访问");
        if (owner == actor) return;
        if (!"EDITOR".equals(meetings.memberRole(meetingId, actor)))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "会议不存在或无权访问");
    }

    public void requireOutlineEditor(String meetingId, long actor, boolean lock) {
        requireEditor(meetingId, actor, lock);
    }
}
