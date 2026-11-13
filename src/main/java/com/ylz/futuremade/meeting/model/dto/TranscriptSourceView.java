package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 某个文字稿版本关联的 AI 草稿来源信息。 */
@Getter @Setter
public class TranscriptSourceView {
    /** 对应文字稿版本号。 */ private Integer transcriptVersion;
    /** 来源 AI 草稿 ID，手工文字稿为空。 */ private String draftId;
    /** 关联 AI 调用记录 ID，手工文字稿为空。 */ private Long aiCallId;
    /** 有序的识别任务与校对版本快照；手工文字稿为空列表。 */ private List<TranscriptionSource> sources;
}
