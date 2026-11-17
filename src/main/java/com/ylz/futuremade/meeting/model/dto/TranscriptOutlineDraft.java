package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 基于已保存文字稿生成的大纲草稿及来源快照。
 */
@Getter
@Setter
public class TranscriptOutlineDraft {
    /**
     * 按会议顺序排列的大纲标题。
     */
    private List<String> titles;
    /**
     * 大纲条目、候选内容及文字稿分段的来源快照。
     */
    private OutlineSourceSnapshot source;
}
