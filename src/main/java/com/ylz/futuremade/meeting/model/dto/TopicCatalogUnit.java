package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 跨批次归并时供模型比较的议题目录项。 */
@Getter
@Setter
public class TopicCatalogUnit implements TopicGroupingInput {
    /** 本轮归并目录中的唯一编号。 */
    private String id;
    /** 待比较的议题标题。 */
    private String topic;
    /** 辅助判断议题语义的内容样例。 */
    private List<TopicSample> samples;
}
