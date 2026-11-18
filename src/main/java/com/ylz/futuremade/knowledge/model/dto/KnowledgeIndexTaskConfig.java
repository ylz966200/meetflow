package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 纪要索引任务固定的业务输入，不包含敏感正文。
 */
@Getter
@Setter
public class KnowledgeIndexTaskConfig {
    /**
     * 需要索引的已确认纪要版本。
     */
    private Integer minutesVersion;
}
