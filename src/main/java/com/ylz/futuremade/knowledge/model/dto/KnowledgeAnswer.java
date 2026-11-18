package com.ylz.futuremade.knowledge.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 单场会议知识检索或问答结果。 */
@Getter @Setter
public class KnowledgeAnswer {
    /** 已确认的会议纪要版本。 */
    private int minutesVersion;
    /** 生成的回答，索引操作时为空。 */
    private String answer;
    /** 本次提供给模型的参考纪要条目。 */
    private List<KnowledgeSource> sources;
}
