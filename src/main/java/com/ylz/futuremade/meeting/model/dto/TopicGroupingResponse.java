package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 议题归并模型返回的分组列表。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopicGroupingResponse {
    /** 模型给出的分组结果。 */
    private List<TopicGroupingGroup> groups;
}
