package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
/** 火山录音识别查询响应。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolcTranscriptionResponse {
    /** 识别结果。 */
    private VolcTranscriptionResult result;
}
