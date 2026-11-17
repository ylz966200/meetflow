package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 火山语音请求中的调用方。 */
@Getter
@Setter
public class VolcSubmitUser {
    /** 调用方标识，不代表会议发言人。 */
    private String uid;
}
