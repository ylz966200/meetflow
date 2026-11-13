package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.util.List;

/** 按用户指定顺序合并多个识别任务的请求。 */
@Getter @Setter
public class AssembleTranscriptionsRequest {
    /** 要合并的成功转写任务 ID，顺序决定文字内容顺序。 */
    private List<String> taskIds;
}
