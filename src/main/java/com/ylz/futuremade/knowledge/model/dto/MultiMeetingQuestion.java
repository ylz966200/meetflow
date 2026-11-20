package com.ylz.futuremade.knowledge.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 跨多场会议提问或清除会话的请求。 */
@Getter
@Setter
public class MultiMeetingQuestion {
    /** 用户的问题，清除记忆时为空。 */
    private String question;
    /** 前端会话 UUID。 */
    private String conversationId;
    /** 是否跳过回答缓存。 */
    private Boolean bypassCache;
    /** 选定的会议及纪要版本。 */
    private List<KnowledgeMeetingSelection> meetings;
}
