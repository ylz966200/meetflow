package com.ylz.futuremade.prompt.model.entity;

import lombok.Getter;
import lombok.Setter;
/** 提示词业务场景表 mf_prompt_scenario 对应的数据模型。 */
@Getter @Setter
public class PromptScenario {
    /** 稳定的场景编码。 */ private String code;
    /** 场景名称。 */ private String name;
    /** 当前生效的版本 ID。 */ private Long activeVersionId;
}
