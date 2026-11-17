package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 跨批议题归并使用的简短候选信息样例。 */
@Getter
@Setter
public class TopicSample {
    /** 原候选信息的主题。 */
    private String topic;
    /** 按配置截取的候选内容，仅用于辅助归并。 */
    private String content;
}
