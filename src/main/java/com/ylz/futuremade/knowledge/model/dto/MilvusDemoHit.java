package com.ylz.futuremade.knowledge.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 一条 Milvus 虚构样本检索结果。 */
@Getter @Setter
public class MilvusDemoHit {
    /** 样本的固定主键。 */
    private String id;
    /** 检索到的虚构样本文本。 */
    private String text;
    /** COSINE 相似度，数值越大通常越相似，不表示正确率。 */
    private float score;
}
