package com.ylz.futuremade.oa.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ylz.futuremade.meeting.mapper.MinutesMapper;
import com.ylz.futuremade.meeting.model.entity.MinutesVersionModel;
import com.ylz.futuremade.meeting.model.dto.StructuredMinutes;
import com.ylz.futuremade.meeting.model.dto.MinutesActionItem;
import com.ylz.futuremade.meeting.model.dto.MinutesSection;
import com.ylz.futuremade.oa.model.dto.OaTaskView;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 为已经通过会议权限校验的待办列表补充固定版本的大纲来源。 */
@Service
public class OaTaskSourceService {
    private final MinutesMapper minutes;
    private final ObjectMapper json;

    public OaTaskSourceService(MinutesMapper minutes, ObjectMapper json) {
        this.minutes = minutes;
        this.json = json;
    }

    /** 只读取待办已绑定的历史纪要，按版本缓存，避免逐条重复读取和解析。 */
    public void enrich(String meetingId, List<OaTaskView> tasks) {
        Map<Integer, MinutesVersionModel> versions = new HashMap<>();
        Map<Integer, StructuredMinutes> documents = new HashMap<>();
        for (OaTaskView task : tasks) {
            Integer version = task.getMinutesVersion();
            if (!versions.containsKey(version)) {
                MinutesVersionModel row = minutes.find(meetingId, version);
                versions.put(version, row);
                if (row != null && row.getStructuredJson() != null) {
                    try {
                        documents.put(version, json.readValue(row.getStructuredJson(), StructuredMinutes.class));
                    } catch (JsonProcessingException ex) {
                        throw new IllegalStateException("来源纪要结构无法解析", ex);
                    }
                }
            }
            MinutesVersionModel row = versions.get(version);
            if (row == null) continue;
            task.setOutlineVersion(row.getOutlineVersion());
            StructuredMinutes document = documents.get(version);
            // 历史纯文本纪要或已缺失的行动项不能推断来源，保持未记录。
            if (document == null || document.getActions() == null || task.getActionIndex() == null
                    || task.getActionIndex() < 0 || task.getActionIndex() >= document.getActions().size()) continue;
            MinutesActionItem action = document.getActions().get(task.getActionIndex());
            if (action.getOutlineId() == null) continue;
            task.setOutlineId(action.getOutlineId());
            if (document.getSections() == null) continue;
            for (MinutesSection section : document.getSections()) {
                if (action.getOutlineId().equals(section.getOutlineId())) {
                    task.setOutlineTitle(section.getTitle());
                    break;
                }
            }
        }
    }
}
