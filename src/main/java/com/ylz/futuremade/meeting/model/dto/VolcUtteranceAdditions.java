package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
/** 火山识别发言中的说话人信息。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolcUtteranceAdditions {
    /** 火山返回的说话人编号，部分版本可能为空。 */
    private String speaker;
    /** 兼容另一版本返回的说话人编号。 */
    @JsonProperty("speaker_id") private String speakerId;
}
