package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 会议大纲版本表（mf_outline_version）对应的数据模型。 */
@Getter
@Setter
public class OutlineVersionModel {
    /** 会议 ID。 */
    private String meetingId;

    /** 大纲版本号。 */
    private Integer version;

    /** 关联的文字稿版本号。 */
    private Integer transcriptVersion;

    /** 大纲状态码，参见 BusinessStatus 枚举。 */
    private Integer status;

    /** 按顺序保存大纲条目的 JSON。 */
    private String itemsJson;

    /** 大纲版本创建人用户 ID。 */
    private Long createdBy;

    /** 大纲版本创建时间。 */
    private LocalDateTime createdAt;

}
