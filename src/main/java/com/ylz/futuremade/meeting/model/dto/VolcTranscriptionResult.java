package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 火山识别结果中的逐句发言。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class VolcTranscriptionResult {
    /** 按时间排序的识别发言。 */
    private List<VolcUtterance> utterances;
}
