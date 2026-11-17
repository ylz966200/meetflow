package com.ylz.futuremade.meeting.model.entity;
import lombok.Getter;
import lombok.Setter;
/** 会议大纲版本对应的候选信息与文字稿来源快照。 */
@Getter @Setter
public class OutlineSourceModel {
    /** 所属会议 ID。 */ private String meetingId;
    /** 所属大纲版本。 */ private Integer outlineVersion;
    /** 大纲分组、候选内容及文字稿片段 JSON。 */ private String sourceJson;
}
