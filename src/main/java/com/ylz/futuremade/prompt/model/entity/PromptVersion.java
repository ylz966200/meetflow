package com.ylz.futuremade.prompt.model.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 提示词不可变版本表 mf_prompt_version 对应的数据模型。
 */
@Getter
@Setter
public class PromptVersion {
    /**
     * 版本主键。
     */
    private Long id;
    /**
     * 业务场景编码。
     */
    private String scenario;
    /**
     * 场景内递增的版本号。
     */
    private Integer version;
    /**
     * 系统提示词内容。
     */
    private String content;
    /**
     * 修改原因。
     */
    private String changeNote;
    /**
     * 创建人用户 ID；系统初始化时为空。
     */
    private Long createdBy;
    /**
     * 创建时间。
     */
    private LocalDateTime createdAt;
}
