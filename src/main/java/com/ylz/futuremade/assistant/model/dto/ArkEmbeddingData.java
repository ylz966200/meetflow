package com.ylz.futuremade.assistant.model.dto;

import lombok.Getter;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 方舟多模态向量响应中的稠密向量。 */
@Getter @Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArkEmbeddingData {
    /** 浮点数向量，实际维度由模型决定。 */
    private float[] embedding;
}
