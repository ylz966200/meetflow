package com.ylz.futuremade.assistant.model.dto;

import java.util.List;
import lombok.Getter;

/** 方舟多模态向量化请求，每次请求生成一条文本的向量。 */
@Getter
public class ArkEmbeddingRequest {
    /** 方舟模型或推理接入点 ID。 */
    private final String model;
    /** 文本输入列表；本实现每次仅包含一条独立文本。 */
    private final List<ArkEmbeddingInput> input;

    public ArkEmbeddingRequest(String model, String text) {
        this.model = model;
        this.input = List.of(new ArkEmbeddingInput(text));
    }
}
