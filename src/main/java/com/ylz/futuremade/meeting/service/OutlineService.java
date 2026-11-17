package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;
import com.ylz.futuremade.meeting.model.dto.OutlineView;

import com.ylz.futuremade.meeting.mapper.MeetingMapper;
import com.ylz.futuremade.meeting.mapper.OutlineMapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class OutlineService {
    private final OutlineMapper outlines;
    private final MeetingMapper meetings;
    private final MeetingService access;
    private final ObjectMapper json;
    private final OutlineSourceService sources;

    public OutlineService(OutlineMapper outlines, MeetingMapper meetings, MeetingService access, ObjectMapper json, OutlineSourceService sources) {
        this.outlines = outlines;
        this.meetings = meetings;
        this.access = access;
        this.json = json;
        this.sources = sources;
    }

    public OutlineView latest(String id, long actor) {
        access.requireOutlineEditor(id, actor, false);
        int version = outlines.latestVersion(id);
        if (version == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "暂无大纲");
        return view(id, outlines.find(id, version));
    }

    public OutlineView history(String id, long actor, int version) {
        access.requireOutlineEditor(id, actor, false);
        var outline = outlines.find(id, version);
        if (outline == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "大纲不存在");
        return view(id, outline);
    }

    /** 分页返回指定版本的大纲标题和来源编号；不加载来源正文。 */
    public com.ylz.futuremade.meeting.model.dto.OutlinePage page(String id, long actor, Integer requestedVersion, int page, int size) {
        access.requireOutlineEditor(id, actor, false);
        if (page < 1 || size < 1 || size > 100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "分页参数无效");
        int version = requestedVersion == null ? outlines.latestVersion(id) : requestedVersion;
        var row = outlines.find(id, version);
        if (row == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "大纲不存在");
        try {
            List<String> titles = json.readValue(row.itemsJson(), new TypeReference<List<String>>() {});
            var snapshot = sources.findStored(id, version);
            int start = (int) Math.min((long) (page - 1) * size, titles.size());
            int end = Math.min(start + size, titles.size());
            var result = new com.ylz.futuremade.meeting.model.dto.OutlinePage();
            result.setVersion(version);
            result.setTranscriptVersion(row.transcriptVersion());
            result.setStatus(row.status());
            result.setStale(row.transcriptVersion() != meetings.latestVersion(id));
            result.setPage(page);
            result.setSize(size);
            result.setTotal(titles.size());
            result.setItems(titles.subList(start, end));
            result.setHasSources(snapshot != null);
            result.setPartialGrouping(snapshot != null && Boolean.TRUE.equals(snapshot.getPartialGrouping()));
            var ids = new java.util.ArrayList<List<String>>();
            for (int i = start; i < end; i++) ids.add(snapshot == null ? List.of() : snapshot.getGroups().get(i).getItemIds());
            result.setItemSourceIds(ids);
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("大纲数据无法读取", ex);
        }
    }

    /** 在会议权限范围内读取指定大纲版本的候选内容。 */
    public com.ylz.futuremade.meeting.model.dto.OutlineSourceDetail sourceDetail(String id, long actor, int version, List<String> ids) {
        access.requireOutlineEditor(id, actor, false);
        if (version < 1 || ids == null || ids.isEmpty() || ids.size() > 200 || ids.stream().anyMatch(java.util.Objects::isNull))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "来源编号无效");
        if (outlines.find(id, version) == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "大纲不存在");
        return sources.detail(id, version, ids);
    }

    /** 校验操作权限及生成所依赖的版本；文字稿分片仍归属于对应的文字稿版本。 */
    public void checkGeneration(String id, long actor, int transcriptVersion, int expectedVersion) {
        access.requireOutlineEditor(id, actor, false);
        if (transcriptVersion <= 0 || expectedVersion < 0)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文字稿或大纲版本无效");
        if (meetings.latestVersion(id) != transcriptVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文字稿已更新，请重新加载后生成大纲");
        if (outlines.latestVersion(id) != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "大纲已更新，请重新加载后再试");
    }

    @Transactional
    public OutlineView save(String id, long actor, int transcriptVersion, int expectedVersion, List<String> items, boolean confirm) {
        return save(id, actor, transcriptVersion, expectedVersion, items, confirm, false);
    }

    /** 生成草稿时即使标题相同也保留新的来源快照。 */
    @Transactional
    public OutlineView save(String id, long actor, int transcriptVersion, int expectedVersion, List<String> items, boolean confirm, boolean forceNew) {
        access.requireOutlineEditor(id, actor, true);
        if (confirm && !Long.valueOf(actor).equals(meetings.owner(id)))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅组织者可以确认大纲");
        if (transcriptVersion <= 0 || meetings.transcript(id, transcriptVersion) == null)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "文字稿版本不存在");
        if (meetings.latestVersion(id) != transcriptVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文字稿已更新，请重新生成大纲");
        if (outlines.latestVersion(id) != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "大纲已更新，请重新加载");
        if (confirm && (expectedVersion == 0 || !BusinessStatus.DRAFT.code().equals(outlines.find(id, expectedVersion).status())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "请先生成或保存大纲草稿");
        if (items == null || items.isEmpty() || items.size() > 200 || items.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 200))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大纲需要1至200个非空条目，每项最多200个字符");
        try {
            var normalized = items.stream().map(String::strip).toList();
            if (!confirm && !forceNew && expectedVersion > 0) {
                var previous = outlines.find(id, expectedVersion);
                var previousItems = json.readValue(previous.itemsJson(), new TypeReference<List<String>>() {});
                if (previous.transcriptVersion() == transcriptVersion && previousItems.stream().map(String::strip).toList().equals(normalized))
                    return view(id, previous);
            }
            outlines.insert(id, expectedVersion + 1, transcriptVersion, confirm ? BusinessStatus.CONFIRMED.code() : BusinessStatus.DRAFT.code(), json.writeValueAsString(normalized), actor);
            return view(id, outlines.find(id, expectedVersion + 1));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("大纲序列化失败", ex);
        }
    }

    private OutlineView view(String id, OutlineMapper.Outline value) {
        try {
            var result = new OutlineView();
            result.setVersion(value.version());
            result.setTranscriptVersion(value.transcriptVersion());
            result.setStatus(value.status());
            result.setItems(json.readValue(value.itemsJson(), new TypeReference<List<String>>() {}));
            result.setCreatedAt(value.createdAt());
            result.setStale(value.transcriptVersion() != meetings.latestVersion(id));
            result.setSources(readSources(id, value.version()));
            return result;
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("大纲数据无法读取", ex);
        }
    }

    private com.ylz.futuremade.meeting.model.dto.OutlineSourceSnapshot readSources(String meetingId, int version) {
        return sources.find(meetingId, version);
    }

}
