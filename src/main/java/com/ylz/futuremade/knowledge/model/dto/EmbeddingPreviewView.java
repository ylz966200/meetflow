package com.ylz.futuremade.knowledge.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 向量化实验结果，只展示部分数值。 */
@Getter @Setter
public class EmbeddingPreviewView {
    /** 配置的文本向量模型或接入点 ID。 */
    private String model;
    /** 实际返回的向量维度。 */
    private int dimension;
    /** 向量开头最多八个数值，仅供学习观察。 */
    private List<Float> sample;
}
