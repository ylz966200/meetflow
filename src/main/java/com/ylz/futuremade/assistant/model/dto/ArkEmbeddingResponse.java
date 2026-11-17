package com.ylz.futuremade.assistant.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

/** 方舟多模态向量响应，仅提取当前使用的结果。 */
@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArkEmbeddingResponse {
    /** 方舟返回的数据对象。 */
    private ArkEmbeddingData data;
}
