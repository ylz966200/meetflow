package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
import java.util.Map;
/** 大纲分组及其候选内容、原文来源的版本快照。 */
@Getter
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class OutlineSourceSnapshot {
    /** 按展示顺序排列的大纲分组。 */
    private List<OutlineSourceGroup> groups;
    /** 候选编号与对应候选内容的映射。 */
    private Map<String, TopicCandidate> items;
    /** 是否需要人工审核。 */
    private Boolean needsReview;
    /** 是否存在未完成全局比较的分组。 */
    private Boolean partialGrouping;
    /** 当前版本引用的文字稿片段 ID；历史快照可能为空。 */
    private List<Long> segmentIds;
    /** 来源文字稿的片段内容；新快照持久化时不重复保存，读取时还原。 */
    private List<OutlineSourceSegment> sourceSegments;
    /** 来源文字稿版本；旧快照可能为空。 */
    private Integer transcriptVersion;
}
