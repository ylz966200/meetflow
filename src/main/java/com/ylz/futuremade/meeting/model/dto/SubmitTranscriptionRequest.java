package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 通过远程音频链接提交转写任务的请求。 */
@Getter @Setter
public class SubmitTranscriptionRequest {
    /** 火山语音服务可访问的 HTTPS 音频链接，不持久化。 */ private String audioUrl;
    /** 音频容器格式：wav、mp3 或 ogg。 */ private String format;
}
