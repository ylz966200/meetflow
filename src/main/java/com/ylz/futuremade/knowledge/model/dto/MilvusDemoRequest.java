package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 使用固定虚构样本演示向量检索的请求。 */
@Getter @Setter
public class MilvusDemoRequest {
    /** 要进行语义检索的实验问题，不得包含真实会议敏感内容。 */
    private String query;
}
