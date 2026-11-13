package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 按选定转写结果生成文字稿草稿。 */
@Getter @Setter
public class OrganizeTranscriptRequest {
    /** 按用户选择顺序排列的转写任务 ID。 */ private List<String> taskIds;
    /** 生成时的当前文字稿版本，首次为 0。 */ private Integer expectedVersion;
}
