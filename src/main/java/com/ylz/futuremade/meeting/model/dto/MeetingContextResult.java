package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 上下文工具返回的发言和查询状态，不代表模型已经确认其语义。 */
@Getter @Setter
public class MeetingContextResult {
    /** 本次查询的提示；没有匹配或达到调用上限时说明原因。 */ private String message;
    /** 按原始发言顺序返回的上下文片段。 */ private List<ChunkUtterance> utterances;
    /** 本次结果是否因数量或长度限制而未包含所有匹配。 */ private boolean truncated;
}
