package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** 创建会议请求。 */
@Getter
@Setter
public class CreateMeetingRequest {
    /** 会议主题，不能为空。 */
    private String title;

    /** 会议时间，可为空；当前按应用约定时区解释。 */
    private LocalDateTime meetingTime;

}
