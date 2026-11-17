package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 大纲后台任务的固定生成配置。 */
@Getter @Setter
public class OutlineTaskConfig {
    /** 任务创建时生效的候选信息归并提示词版本 ID。 */ private Long groupingPromptVersionId;
}
