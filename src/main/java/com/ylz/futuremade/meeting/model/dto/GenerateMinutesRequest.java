package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 生成会议纪要草稿的请求。 */
@Getter @Setter
public class GenerateMinutesRequest {
    /** 请求时看到的已确认大纲版本号。 */ private Integer outlineVersion;
    /** 请求时看到的最新纪要版本号，无纪要时为 0。 */ private Integer expectedVersion;
}
