package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 会议纪要检索命中的可追溯内容。
 */
@Getter
@Setter
public class KnowledgeSource {
    /** 来源会议 ID。 */
    private String meetingId;
    /** 来源会议名称。 */
    private String meetingTitle;
    /** 来源纪要版本。 */
    private Integer minutesVersion;
    /**
     * 纪要条目序号，从零开始。
     */
    private int sectionIndex;
    /**
     * 大纲标题。
     */
    private String title;
    /**
     * 已确认的纪要内容。
     */
    private String content;
    /**
     * 向量余弦相似度，不代表答案正确率。
     */
    private float score;
}
