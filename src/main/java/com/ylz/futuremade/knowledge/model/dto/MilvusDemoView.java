package com.ylz.futuremade.knowledge.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 虚构数据向量检索实验的结果与关键配置。 */
@Getter @Setter
public class MilvusDemoView {
    /** 专用于该模型与维度的实验集合名称。 */
    private String collection;
    /** 文档和问题使用的实际向量维度。 */
    private int dimension;
    /** 虚构样本条数。 */
    private int sampleCount;
    /** 问题向量的前几个数值，供学习观察。 */
    private List<Float> vectorSample;
    /** 按相似度排列的检索结果。 */
    private List<MilvusDemoHit> hits;
}
