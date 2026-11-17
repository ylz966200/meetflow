package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 火山语音请求中的音频信息。 */
@Getter
@Setter
public class VolcSubmitAudio {
    /** 私有音频的临时访问地址，不得写入日志。 */
    private String url;
    /** 火山服务接受的音频格式。 */
    private String format;
}
