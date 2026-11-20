package com.ylz.futuremade.evaluation.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 样本使用的会议及固定版本。 */
@Getter @Setter
public class KnowledgeEvaluationMeeting {
    /** 数据集内会议简称。 */ private String key;
    /** 会议 ID。 */ private String meetingId;
    /** 会议名称。 */ private String title;
    /** 已确认纪要版本。 */ private Integer minutesVersion;
    /** 大纲版本。 */ private Integer outlineVersion;
    /** 文字稿版本。 */ private Integer transcriptVersion;
}
