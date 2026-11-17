package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 管理员提交的向量化实验文本，建议使用虚构数据。 */
@Getter @Setter
public class EmbeddingPreviewRequest {
    /** 需要向量化的非空文本，最多 1000 个字符。 */
    private String text;
}
