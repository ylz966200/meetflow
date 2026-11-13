package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 多条转写的合并输入预览和对应来源信息。 */
@Getter @Setter
public class AssembledTranscriptions {
    /** 按任务顺序和片段顺序拼接的文字，未经 AI 整理。 */ private String content;
    /** 所用任务及说话人校对版本，顺序与内容一致。 */ private List<TranscriptionSource> sources;
}
