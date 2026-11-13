package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 保存或确认会议纪要的请求。 */
@Getter @Setter
public class SaveMinutesRequest {
    /** 正文所依据的已确认大纲版本号。 */ private Integer outlineVersion;
    /** 请求时看到的最新纪要版本号。 */ private Integer expectedVersion;
    /** 人工编辑后的会议纪要正文。 */ private String content;
    /** 是否确认纪要，只有组织者可以确认。 */ private Boolean confirm;
}
