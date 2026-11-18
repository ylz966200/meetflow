package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;

import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.mapper.MinutesMapper;
import com.ylz.futuremade.meeting.mapper.OutlineMapper;
import com.ylz.futuremade.meeting.model.dto.MinutesView;
import com.ylz.futuremade.meeting.model.dto.OutlineView;
import com.ylz.futuremade.meeting.model.entity.MinutesVersionModel;
import com.ylz.futuremade.meeting.model.dto.StructuredMinutes;
import com.ylz.futuremade.meeting.model.dto.MinutesSection;
import com.ylz.futuremade.meeting.model.dto.MinutesPoint;
import com.ylz.futuremade.meeting.model.dto.MinutesActionItem;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.knowledge.service.KnowledgeIndexTaskService;

import java.util.Objects;
import java.util.ArrayList;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * 管理纪要版本、权限及来源版本一致性。
 */
@Service
public class MinutesService {
    /**
     * 纪要正文最大长度，按 Java UTF-16 单元计数；与页面输入限制一致。
     */
    public static final int MAX_CONTENT_LENGTH = 200_000;
    private final MeetingService access;
    private final MeetingMapper meetings;
    private final OutlineMapper outlines;
    private final MinutesMapper minutes;
    private final ObjectMapper json;
    private final KnowledgeIndexTaskService indexTasks;

    public MinutesService(MeetingService access, MeetingMapper meetings, OutlineMapper outlines, MinutesMapper minutes, ObjectMapper json, KnowledgeIndexTaskService indexTasks) {
        this.access = access;
        this.meetings = meetings;
        this.outlines = outlines;
        this.minutes = minutes;
        this.json = json;
        this.indexTasks = indexTasks;
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
        return saveVersion(id, actor, outlineVersion, expectedVersion, content, confirm, null);
    }

    /**
     * 保存模型生成的结构化草稿，来源来自服务端已校验的批次结果。
     */
    @Transactional
    public MinutesView saveGenerated(String id, long actor, int outlineVersion, int expectedVersion, StructuredMinutes document) {
        return saveVersion(id, actor, outlineVersion, expectedVersion, MinutesStructure.format(document), false, document);
    }

    /**
     * 保存用户编辑的条目；来源及行动项以原版本为准，修改过的条目标记待复核。
     */
    @Transactional
    public MinutesView saveStructured(String id, long actor, int outlineVersion, int expectedVersion, StructuredMinutes input, boolean confirm) {
        access.requireOutlineEditor(id, actor, true);
        if (minutes.latestVersion(id) != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "纪要已更新，请重新加载");
        var previous = minutes.find(id, expectedVersion);
        if (previous == null || previous.getStructuredJson() == null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "当前版本没有结构化纪要，请保留原文或重新生成");
        try {
            var original = json.readValue(previous.getStructuredJson(), StructuredMinutes.class);
            if (input == null || input.getSections() == null || input.getSections().size() != original.getSections().size())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "纪要条目不完整，请重新加载");
            var sections = new ArrayList<MinutesSection>();
            for (int i = 0; i < original.getSections().size(); i++) {
                var old = original.getSections().get(i);
                var edit = input.getSections().get(i);
                if (edit == null || !Objects.equals(old.getOutlineId(), edit.getOutlineId()))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "纪要条目顺序或正文无效");
                var section = new MinutesSection();
                section.setOutlineId(old.getOutlineId());
                section.setTitle(old.getTitle());
                if (old.getDiscussion() == null) {
                    if (edit.getContent() == null || edit.getContent().isBlank())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "纪要正文不能为空");
                    section.setContent(edit.getContent().strip());
                } else {
                    section.setDiscussion(editPoints(old.getDiscussion(), edit.getDiscussion()));
                    section.setConclusions(editPoints(old.getConclusions(), edit.getConclusions()));
                    if (section.getDiscussion().isEmpty() && section.getConclusions().isEmpty())
                        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "讨论内容和会议结论不能同时为空");
                }
                section.setSourceSegmentIds(old.getSourceSegmentIds());
                section.setNeedsReview(Boolean.TRUE.equals(old.getNeedsReview()) || old.getDiscussion() == null
                        && !ContentComparison.normalize(old.getContent()).equals(ContentComparison.normalize(edit.getContent())));
                sections.add(section);
            }
            if (input.getActions() == null || input.getActions().size() != original.getActions().size())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行动项不完整，请重新加载");
            var actions = new ArrayList<MinutesActionItem>();
            for (int i = 0; i < original.getActions().size(); i++) {
                var old = original.getActions().get(i);
                var edit = input.getActions().get(i);
                if (edit == null || !Objects.equals(old.getOutlineId(), edit.getOutlineId()) || edit.getContent() == null || edit.getContent().isBlank())
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "行动项顺序或正文无效");
                var action = new MinutesActionItem();
                action.setOutlineId(old.getOutlineId());
                action.setContent(edit.getContent().strip());
                action.setAssignee(edit.getAssignee());
                action.setDeadline(edit.getDeadline());
                action.setSourceSegmentIds(old.getSourceSegmentIds());
                action.setNeedsReview(Boolean.TRUE.equals(old.getNeedsReview()) || !Objects.equals(old.getContent(), action.getContent())
                        || !Objects.equals(old.getAssignee(), action.getAssignee()) || !Objects.equals(old.getDeadline(), action.getDeadline()));
                actions.add(action);
            }
            var updated = new StructuredMinutes();
            updated.setSections(sections);
            updated.setActions(actions);
            return saveVersion(id, actor, outlineVersion, expectedVersion, MinutesStructure.format(updated), confirm, updated);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("纪要结构无法读取", ex);
        }
    }

    private java.util.List<MinutesPoint> editPoints(java.util.List<MinutesPoint> original, java.util.List<MinutesPoint> edits) {
        if (original == null || edits == null || original.size() > edits.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "纪要内容条目已变化，请重新加载");
        var result = new ArrayList<MinutesPoint>();
        for (int i = 0; i < edits.size(); i++) {
            var old = i < original.size() ? original.get(i) : null; var edit = edits.get(i);
            if (edit == null || edit.getContent() == null || edit.getContent().isBlank())
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "讨论内容和会议结论不能为空");
            var point = new MinutesPoint();
            point.setContent(edit.getContent().strip());
            point.setSourceSegmentIds(old == null ? java.util.List.of() : old.getSourceSegmentIds());
            point.setNeedsReview(old == null || Boolean.TRUE.equals(old.getNeedsReview()) || !ContentComparison.normalize(old.getContent()).equals(ContentComparison.normalize(edit.getContent())));
            result.add(point);
        }
        return result;
    }

    private MinutesView saveVersion(String id, long actor, int outlineVersion, int expectedVersion, String content, boolean confirm, StructuredMinutes structured) {
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
                    && ContentComparison.normalize(previous.getContent()).equals(ContentComparison.normalize(content))
                    && Objects.equals(previous.getStructuredJson(), encode(structured)))
                return view(id, previous);
        }
        MinutesVersionModel row = new MinutesVersionModel();
        row.setMeetingId(id);
        row.setVersion(expectedVersion + 1);
        row.setOutlineVersion(outlineVersion);
        row.setTranscriptVersion(outline.transcriptVersion());
        row.setStatus(confirm ? BusinessStatus.CONFIRMED.code() : BusinessStatus.DRAFT.code());
        row.setContent(content.strip());
        row.setStructuredJson(encode(structured));
        row.setCreatedBy(actor);
        minutes.insert(row);
        // 任务与纪要在同一事务中写入，进程退出后仍可由后台扫描恢复。
        if (confirm)
            indexTasks.create(id, row.getVersion(), row.getTranscriptVersion(), row.getOutlineVersion(), actor);
        return view(id, minutes.find(id, expectedVersion + 1));
    }

    private MinutesView view(String id, MinutesVersionModel row) {
        MinutesView view = new MinutesView();
        view.setVersion(row.getVersion());
        view.setOutlineVersion(row.getOutlineVersion());
        view.setTranscriptVersion(row.getTranscriptVersion());
        view.setStatus(row.getStatus());
        view.setContent(row.getContent());
        if (row.getStructuredJson() != null) {
            try {
                view.setStructured(json.readValue(row.getStructuredJson(), StructuredMinutes.class));
            } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
                throw new IllegalStateException("纪要结构无法读取", ex);
            }
        }
        view.setCreatedAt(row.getCreatedAt());
        view.setStale(outlines.latestVersion(id) != row.getOutlineVersion() || meetings.latestVersion(id) != row.getTranscriptVersion());
        return view;
    }

    private String encode(StructuredMinutes document) {
        if (document == null) return null;
        try {
            return json.writeValueAsString(document);
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("纪要结构无法保存", ex);
        }
    }
}
