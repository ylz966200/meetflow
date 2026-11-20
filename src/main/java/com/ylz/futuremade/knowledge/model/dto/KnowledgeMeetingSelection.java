package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 知识问答中选定的会议及纪要版本。 */
@Getter
@Setter
public class KnowledgeMeetingSelection {
    /** 会议 ID。 */
    private String meetingId;
    /** 对话固定的纪要版本，首次提问可为空。 */
    private Integer minutesVersion;
    /** 会议名称，由服务端返回。 */
    private String meetingTitle;
}
