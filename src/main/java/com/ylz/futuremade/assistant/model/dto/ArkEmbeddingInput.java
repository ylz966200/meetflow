package com.ylz.futuremade.assistant.model.dto;

import lombok.Getter;

/** 方舟多模态向量接口的一条文本输入。 */
@Getter
public class ArkEmbeddingInput {
    /** 输入类型：方舟协议中的文本类型。 */
    private final String type = "text";
    /** 需要向量化的文本。 */
    private final String text;

    public ArkEmbeddingInput(String text) { this.text = text; }
}
