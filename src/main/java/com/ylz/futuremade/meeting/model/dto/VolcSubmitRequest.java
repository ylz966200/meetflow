package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 火山录音文件识别任务的提交请求。 */
@Getter
@Setter
public class VolcSubmitRequest {
    /** 请求调用方标识。 */
    private VolcSubmitUser user;
    /** 待识别音频信息。 */
    private VolcSubmitAudio audio;
    /** 语音识别参数。 */
    private VolcSubmitOptions request;
}
