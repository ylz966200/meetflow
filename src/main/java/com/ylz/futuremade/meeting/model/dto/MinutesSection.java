package com.ylz.futuremade.meeting.model.dto;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 与已确认大纲关联的一条纪要内容。
 */
@Getter
@Setter
public class MinutesSection {
    /**
     * 大纲中的序号，从 1 开始。
     */
    private Integer outlineId;
    /**
     * 保存时的大纲标题快照。
     */
    private String title;
    /**
     * 用户可以修改的纪要正文。
     */
    private String content;
    /**
     * 逐条保存的讨论内容；旧版纪要可能为空。
     */
    private List<MinutesPoint> discussion;
    /**
     * 逐条保存的明确会议结论；无结论时为空列表。
     */
    private List<MinutesPoint> conclusions;
    /**
     * 生成时引用的文字稿片段序号，从 0 开始；编辑后仅作为参考。
     */
    private List<Integer> sourceSegmentIds;
    /**
     * 内容或来源需要人工复核。
     */
    private Boolean needsReview;
}
