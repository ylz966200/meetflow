package com.ylz.futuremade.meeting.service;

import com.ylz.futuremade.common.enums.BusinessStatus;

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

    public OutlineService(OutlineMapper outlines, MeetingMapper meetings, MeetingService access, ObjectMapper json) {
        this.outlines = outlines;
        this.meetings = meetings;
        this.access = access;
        this.json = json;
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

    public void checkGeneration(String id, long actor, int transcriptVersion, int expectedVersion) {
        access.requireOutlineEditor(id, actor, false);
        if (meetings.latestVersion(id) != transcriptVersion || outlines.latestVersion(id) != expectedVersion)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "文字稿或大纲已更新，请重新加载");
    }

    @Transactional
    public OutlineView save(String id, long actor, int transcriptVersion, int expectedVersion, List<String> items, boolean confirm) {
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
        if (items == null || items.isEmpty() || items.size() > 30 || items.stream().anyMatch(s -> s == null || s.isBlank() || s.length() > 200))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大纲需要1至30个非空条目，每项最多200个字符");
        try {
            var normalized = items.stream().map(String::strip).toList();
            if (!confirm && expectedVersion > 0) {
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
            return new OutlineView(value.version(), value.transcriptVersion(), value.status(), json.readValue(value.itemsJson(), new TypeReference<List<String>>() {
            }), value.createdAt(), value.transcriptVersion() != meetings.latestVersion(id));
        } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("大纲数据无法读取", ex);
        }
    }

    /** 返回给调用方的大纲版本。
     * @param version 大纲版本号
     * @param transcriptVersion 生成大纲使用的文字稿版本号
     * @param status 大纲状态：草稿或已确认
     * @param items 按顺序排列的大纲标题
     * @param createdAt 版本创建时间
     * @param stale 关联的文字稿是否已不是最新版本
     */
    public record OutlineView(int version, int transcriptVersion, Integer status, List<String> items,
                              java.time.LocalDateTime createdAt, boolean stale) {
    }
}
