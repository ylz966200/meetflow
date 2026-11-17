package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;
/** 会议大纲版本、内容与来源信息。 */
@Getter
@Setter
public class OutlineView {
    /** 大纲版本号。 */
    private Integer version;
    /** 大纲关联的文字稿版本。 */
    private Integer transcriptVersion;
    /** 大纲业务状态码。 */
    private Integer status;
    /** 按顺序排列的大纲标题。 */
    private List<String> items;
    /** 大纲版本创建时间。 */
    private LocalDateTime createdAt;
    /** 来源文字稿是否已更新。 */
    private boolean stale;
    /** 候选信息及原文来源快照；历史版本可能为空。 */
    private OutlineSourceSnapshot sources;
}
