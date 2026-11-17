package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;

import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.mapper.MinutesMapper;
import com.ylz.futuremade.meeting.mapper.OutlineMapper;
import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.meeting.model.dto.OutlineView;
import com.ylz.futuremade.meeting.model.entity.MinutesVersionModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理纪要版本、权限及来源版本一致性。
 */
@Service
public class MinutesService {
    /** 纪要正文最大长度，按 Java UTF-16 单元计数；与页面输入限制一致。 */
    public static final int MAX_CONTENT_LENGTH = 200_000;
    private final MeetingService access;
    private final MeetingMapper meetings;
    private final OutlineMapper outlines;
    private final MinutesMapper minutes;

    public MinutesService(MeetingService access, MeetingMapper meetings, OutlineMapper outlines, MinutesMapper minutes) {
        this.access = access;
        this.meetings = meetings;
        this.outlines = outlines;
        this.minutes = minutes;
    }

    public MinutesView get(String id, long actor, Integer version) {
        access.requireOutlineEditor(id, actor, false);
        int selected = version == null ? minutes.latestVersion(id) : version;
        if (selected <= 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无纪要");
        MinutesVersionModel row = minutes.find(id, selected);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "纪要版本不存在");
        return view(id, row);
    }

    public OutlineView checkGeneration(String id, long actor, int outlineVersion, int expectedVersion, OutlineService service) {
        access.requireOutlineEditor(id, actor, false);
        if (outlineVersion <= 0 || expectedVersion < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "版本无效");
        if (minutes.latestVersion(id) != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "纪要已更新，请重新加载");
        OutlineView outline = service.latest(id, actor);
        if (outline.getVersion() != outlineVersion || !BusinessStatus.CONFIRMED.code().equals(outline.getStatus()) || outline.isStale())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先确认基于最新文字稿的大纲");
        return outline;
    }

    @Transactional
    public MinutesView save(String id, long actor, int outlineVersion, int expectedVersion, String content, boolean confirm) {
        access.requireOutlineEditor(id, actor, true);
        if (outlineVersion <= 0 || expectedVersion < 0 || content == null || content.isBlank() || content.length() > MAX_CONTENT_LENGTH)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "纪要正文不能为空、超过 200000 字符或版本无效");
        if (confirm && !Long.valueOf(actor).equals(meetings.owner(id)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅组织者可以确认纪要");
        if (minutes.latestVersion(id) != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "纪要已更新，请重新加载");
        var outline = outlines.find(id, outlineVersion);
        if (outline == null || outlines.latestVersion(id) != outlineVersion || !BusinessStatus.CONFIRMED.code().equals(outline.status()) || meetings.latestVersion(id) != outline.transcriptVersion())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文字稿或大纲已更新，请重新确认大纲");
        if (confirm) {
            var previous = minutes.find(id, expectedVersion);
            if (previous == null || !BusinessStatus.DRAFT.code().equals(previous.getStatus()) || previous.getOutlineVersion() != outlineVersion)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "请先保存基于当前大纲的纪要草稿");
        }
        if (!confirm && expectedVersion > 0) {
            var previous = minutes.find(id, expectedVersion);
            if (previous.getOutlineVersion() == outlineVersion && previous.getTranscriptVersion() == outline.transcriptVersion()
                    && ContentComparison.normalize(previous.getContent()).equals(ContentComparison.normalize(content)))
                return view(id, previous);
        }
        MinutesVersionModel row = new MinutesVersionModel();
        row.setMeetingId(id);
        row.setVersion(expectedVersion + 1);
        row.setOutlineVersion(outlineVersion);
        row.setTranscriptVersion(outline.transcriptVersion());
        row.setStatus(confirm ? BusinessStatus.CONFIRMED.code() : BusinessStatus.DRAFT.code());
        row.setContent(content.strip());
        row.setCreatedBy(actor);
        minutes.insert(row);
        return view(id, minutes.find(id, expectedVersion + 1));
    }

    private MinutesView view(String id, MinutesVersionModel row) {
        MinutesView view = new MinutesView();
        view.setVersion(row.getVersion());
        view.setOutlineVersion(row.getOutlineVersion());
        view.setTranscriptVersion(row.getTranscriptVersion());
        view.setStatus(row.getStatus());
        view.setContent(row.getContent());
        view.setCreatedAt(row.getCreatedAt());
        view.setStale(outlines.latestVersion(id) != row.getOutlineVersion() || meetings.latestVersion(id) != row.getTranscriptVersion());
        return view;
    }
}
