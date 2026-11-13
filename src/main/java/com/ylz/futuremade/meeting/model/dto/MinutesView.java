package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
/** 返回给会议成员的纪要版本信息。 */
@Getter @Setter
public class MinutesView {
    /** 纪要版本号。 */ private Integer version;
    /** 关联的已确认大纲版本号。 */ private Integer outlineVersion;
    /** 关联的文字稿版本号。 */ private Integer transcriptVersion;
    /** 纪要状态码，参见 BusinessStatus 枚举。 */ private Integer status;
    /** 会议纪要正文。 */ private String content;
    /** 版本创建时间。 */ private LocalDateTime createdAt;
    /** 大纲或文字稿是否已经更新。 */ private Boolean stale;
}
