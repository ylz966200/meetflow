package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 录音转写接口可安全展示的业务错误。 */
@Getter @Setter
public class TranscriptionErrorView {
    /** HTTP 状态码。 */ private int status;
    /** 面向调用方的业务错误说明，不包含会议内容或凭据。 */ private String message;
}
