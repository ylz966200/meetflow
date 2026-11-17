package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ylz.futuremade.common.json.StrictStringDeserializer;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 会议大纲模型返回的标题列表。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class OutlineModelResponse {
    /** 会议大纲条目。 */
    @JsonDeserialize(contentUsing = StrictStringDeserializer.class) private List<String> items;
}
