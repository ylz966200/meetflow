package com.ylz.futuremade.meeting.transcript;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 火山录音文件识别请求中可按部署环境调整的参数。 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "meetflow.speech")
public class SpeechRequestProperties {
    /** 调用方用户标识，不代表录音中的说话人身份。 */
    private String uid = "meetflow";

    /** 说话人分离模型版本，具体支持情况以火山引擎当前服务为准。 */
    private String speakerVersion = "300";
}
