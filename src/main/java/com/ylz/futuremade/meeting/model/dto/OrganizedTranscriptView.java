package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** AI 文字稿草稿及可追溯来源。 */
@Getter @Setter
public class OrganizedTranscriptView {
    /** 草稿 ID；保存文字稿时用于绑定来源。 */ private String draftId;
    /** AI 整理出的待人工确认文本。 */ private String content;
    /** 草稿使用的转写及说话人校对版本快照。 */ private List<TranscriptionSource> sources;
}
