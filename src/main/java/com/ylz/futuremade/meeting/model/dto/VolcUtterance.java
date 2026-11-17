package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.ylz.futuremade.common.json.StrictLongDeserializer;
import lombok.Getter;
import lombok.Setter;
/** 火山识别返回的一段发言。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolcUtterance {
    /** 发言起始时间，单位毫秒。 */
    @JsonProperty("start_time") @JsonDeserialize(using=StrictLongDeserializer.class) private Long startTime;
    /** 发言结束时间，单位毫秒。 */
    @JsonProperty("end_time") @JsonDeserialize(using=StrictLongDeserializer.class) private Long endTime;
    /** 发言识别文本。 */
    private String text;
    /** 说话人等扩展信息，可能为空。 */
    private VolcUtteranceAdditions additions;
}
