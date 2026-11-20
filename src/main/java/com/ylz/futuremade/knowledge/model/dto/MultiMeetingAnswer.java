package com.ylz.futuremade.knowledge.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 多会议问答结果及固定的会议版本。 */
@Getter
@Setter
public class MultiMeetingAnswer {
    /** 模型生成的回答。 */
    private String answer;
    /** 检索时使用的问题，可能由历史对话改写。 */
    private String retrievalQuestion;
    /** 本次问答的模型调用关联标识；缓存命中时可能为空。 */
    private String requestKey;
    /** 本次使用的会议和纪要版本。 */
    private List<KnowledgeMeetingSelection> meetings;
    /** 按会议追溯的参考内容。 */
    private List<KnowledgeSource> sources;
    /** 无检索结果的会议名称。 */
    private List<String> missingMeetings;
    /** 是否命中精确回答缓存。 */
    private boolean cached;
}
