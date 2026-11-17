package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** 用户编辑或确认会议大纲时提交的内容。 */
@Getter
@Setter
public class SaveOutlineRequest extends OutlineVersionRequest {
    /** 按展示顺序排列的大纲条目；每项为非空标题。 */
    private List<String> items;

    /** 每条大纲关联的候选信息编号，顺序与 items 一致；人工新增条目可为空列表。 */
    private List<List<String>> itemSourceIds;

    /** 是否确认大纲；只有会议组织者可以确认，空值按保存草稿处理。 */
    private Boolean confirm;
}
