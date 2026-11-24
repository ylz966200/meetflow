package com.ylz.futuremade.meeting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.mapper.OutlineSourceMapper;
import com.ylz.futuremade.meeting.model.dto.OutlineSourceGroup;
import com.ylz.futuremade.meeting.model.dto.OutlineSourceSnapshot;
import com.ylz.futuremade.meeting.model.entity.OutlineSourceModel;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

/** 保存和校验每个大纲版本的候选内容与来源映射。 */
@Service
public class OutlineSourceService {
    private final OutlineSourceMapper mapper;
    private final ObjectMapper json;
    private final TranscriptSegmentStore segments;

    public OutlineSourceService(OutlineSourceMapper mapper, ObjectMapper json, TranscriptSegmentStore segments) {
        this.mapper = mapper;
        this.json = json;
        this.segments = segments;
    }

    public OutlineSourceSnapshot find(String meetingId, int version) {
        var snapshot = findStored(meetingId, version);
        if (snapshot != null) restoreSegments(meetingId, snapshot);
        return snapshot;
    }

    /** 读取快照，不加载文字稿正文。 */
    public OutlineSourceSnapshot findStored(String meetingId, int version) {
        var source = mapper.find(meetingId, version);
        if (source == null) return null;
        try {
            return json.readValue(source.getSourceJson(), OutlineSourceSnapshot.class);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("大纲来源快照无法读取", ex);
        }
    }

    /** 按候选编号读取来源，仅返回实际引用的文字稿片段。 */
    public com.ylz.futuremade.meeting.model.dto.OutlineSourceDetail detail(String meetingId, int version, List<String> ids) {
        var snapshot = findStored(meetingId, version);
        if (snapshot == null || snapshot.getItems() == null || !snapshot.getItems().keySet().containsAll(ids))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "大纲来源不存在");
        var candidates = ids.stream().map(snapshot.getItems()::get).toList();
        var indices = candidates.stream().flatMap(item -> item.getEvidenceIndices().stream()).distinct().toList();
        var detail = new com.ylz.futuremade.meeting.model.dto.OutlineSourceDetail();
        detail.setItems(candidates);
        detail.setReviewIssues(snapshot.getReviewIssues() == null ? List.of() :
                snapshot.getReviewIssues().stream()
                        .filter(issue -> issue.candidateIds().stream().anyMatch(ids::contains)).toList());
        if (snapshot.getSegmentIds() != null) {
            var allowedIds = new HashSet<>(snapshot.getSegmentIds());
            var rows = segments.findByIndices(meetingId, snapshot.getTranscriptVersion(), indices);
            var selected = new ArrayList<com.ylz.futuremade.meeting.model.dto.OutlineSourceSegment>();
            for (var row : rows) {
                if (!allowedIds.contains(row.getId())) throw new IllegalStateException("大纲来源片段不属于当前快照");
                var segment = new com.ylz.futuremade.meeting.model.dto.OutlineSourceSegment();
                segment.setSegmentIndex(row.getSequenceNo());
                segment.setText(row.getContent());
                selected.add(segment);
            }
            detail.setSegments(selected);
        } else {
            var old = snapshot.getSourceSegments();
            detail.setSegments(old == null ? List.of() : old.stream().filter(item -> indices.contains(item.getSegmentIndex())).toList());
        }
        return detail;
    }

    public void save(String meetingId, int version, OutlineSourceSnapshot source) {
        try {
            var value = new OutlineSourceModel();
            value.setMeetingId(meetingId);
            value.setOutlineVersion(version);
            value.setSourceJson(json.writeValueAsString(forStorage(meetingId, source)));
            mapper.insert(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("大纲来源快照无法保存", ex);
        }
    }

    /** 按结构比较快照，避免 JSON 属性顺序影响版本去重。 */
    public boolean same(OutlineSourceSnapshot first, OutlineSourceSnapshot second) {
        return json.valueToTree(withoutReferences(first)).equals(json.valueToTree(withoutReferences(second)));
    }

    /** 编辑、排序和确认大纲后，按每行携带的候选 ID 保留来源。 */
    public OutlineSourceSnapshot remap(OutlineSourceSnapshot original, List<String> titles, List<List<String>> ids) {
        if (titles == null || ids == null || ids.size() != titles.size())
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请刷新大纲后重新保存来源关联");
        var groups = new ArrayList<OutlineSourceGroup>();
        var used = new HashSet<String>();
        for (int i = 0; i < titles.size(); i++) {
            if (ids.get(i) == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大纲来源无效");
            for (String id : ids.get(i)) {
                if (id == null || !original.getItems().containsKey(id) || !used.add(id))
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "大纲来源编号无效或重复");
            }
            var group = new OutlineSourceGroup();
            group.setTitle(titles.get(i));
            group.setItemIds(List.copyOf(ids.get(i)));
            groups.add(group);
        }
        var source = new OutlineSourceSnapshot();
        source.setGroups(groups);
        source.setItems(original.getItems());
        source.setNeedsReview(original.getNeedsReview());
        source.setPartialGrouping(original.getPartialGrouping());
        source.setReviewIssues(original.getReviewIssues());
        source.setSourceSegments(original.getSourceSegments());
        source.setSegmentIds(original.getSegmentIds());
        source.setTranscriptVersion(original.getTranscriptVersion());
        return source;
    }
    /** 为新快照仅保存稳定片段 ID；不一致的历史快照继续保留原文。 */
    private OutlineSourceSnapshot forStorage(String meetingId, OutlineSourceSnapshot source) {
        var copy=withoutReferences(source);
        if(source.getSegmentIds()!=null) { copy.setSegmentIds(source.getSegmentIds());copy.setSourceSegments(null);return copy; }
        if(source.getTranscriptVersion()==null || source.getSourceSegments()==null) return copy;
        var rows=segments.find(meetingId,source.getTranscriptVersion());
        var originals=source.getSourceSegments();
        if(rows.size()!=originals.size() || rows.isEmpty())return copy;
        for(int i=0;i<rows.size();i++) {
            var row=rows.get(i);var original=originals.get(i);
            if(!java.util.Objects.equals(row.getSequenceNo(),original.getSegmentIndex()) || !java.util.Objects.equals(row.getContent(),original.getText()))return copy;
        }
        copy.setSegmentIds(rows.stream().map(com.ylz.futuremade.meeting.model.entity.TranscriptSegmentModel::getId).toList());
        copy.setSourceSegments(null);return copy;
    }

    /** 只从当前会议和指定文字稿版本加载片段，保持现有接口与历史快照兼容。 */
    private void restoreSegments(String meetingId, OutlineSourceSnapshot source) {
        if(source.getSegmentIds()==null)return;
        if(source.getTranscriptVersion()==null)throw new IllegalStateException("大纲来源缺少文字稿版本");
        var rows=segments.find(meetingId,source.getTranscriptVersion());
        var byId=new java.util.HashMap<Long,com.ylz.futuremade.meeting.model.entity.TranscriptSegmentModel>();
        for(var row:rows)byId.put(row.getId(),row);
        var restored=new ArrayList<com.ylz.futuremade.meeting.model.dto.OutlineSourceSegment>();
        for(Long id:source.getSegmentIds()) {
            var row=byId.get(id);if(row==null)throw new IllegalStateException("大纲引用的文字稿片段不存在或版本不匹配");
            var item=new com.ylz.futuremade.meeting.model.dto.OutlineSourceSegment();item.setSegmentIndex(row.getSequenceNo());item.setText(row.getContent());restored.add(item);
        }
        source.setSourceSegments(restored);
    }

    /** 复制业务字段；避免存储转换修改调用者持有的来源对象。 */
    private OutlineSourceSnapshot withoutReferences(OutlineSourceSnapshot source) {
        if(source==null)return null;
        var copy=new OutlineSourceSnapshot();copy.setGroups(source.getGroups());copy.setItems(source.getItems());
        copy.setNeedsReview(source.getNeedsReview());copy.setPartialGrouping(source.getPartialGrouping());
        copy.setReviewIssues(source.getReviewIssues());
        copy.setSourceSegments(source.getSourceSegments());copy.setTranscriptVersion(source.getTranscriptVersion());return copy;
    }

}
