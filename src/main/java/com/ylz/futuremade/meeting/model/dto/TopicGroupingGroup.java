package com.ylz.futuremade.meeting.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ylz.futuremade.common.json.StrictStringDeserializer;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 模型输出的一条议题及其输入编号。
 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopicGroupingGroup {
    /**
     * 归纳后的议题名称。
     */
    @JsonDeserialize(using = StrictStringDeserializer.class)
    private String topic;
    /**
     * 本组关联的输入编号。
     */
    @JsonDeserialize(contentUsing = StrictStringDeserializer.class)
    private List<String> ids;
}
