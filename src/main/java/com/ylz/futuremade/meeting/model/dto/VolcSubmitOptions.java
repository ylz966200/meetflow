package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
/** 火山录音文件识别选项。 */
@Getter
@Setter
public class VolcSubmitOptions {
    /** 火山协议要求的模型名称。 */
    @JsonProperty("model_name") private String modelName;
    /** 是否启用说话人识别。 */
    @JsonProperty("enable_speaker_info") private boolean enableSpeakerInfo;
    /** 是否返回逐句发言。 */
    @JsonProperty("show_utterances") private boolean showUtterances;
    /** 火山说话人分离模型版本。 */
    @JsonProperty("ssd_version") private String speakerVersion;
}
