package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 一条转写片段经人工确认的说话人；两字段均为空表示尚未确认。 */
@Getter @Setter
public class SpeakerAttribution {
    /** 当前会议成员的用户名；与外部姓名互斥。 */
    private String memberUsername;
    /** 非会议成员的手工姓名，最多 100 字符；与成员用户名互斥。 */
    private String externalName;
}
