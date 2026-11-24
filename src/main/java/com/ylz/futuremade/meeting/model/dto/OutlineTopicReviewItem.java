package com.ylz.futuremade.meeting.model.dto;
/**
 * 整体审核中的议题摘要。
 * @param topicId 本次归并结果的议题编号，从1开始
 * @param title 议题标题
 * @param content 由候选内容提取的有限长度摘要，不能代表完整原文
 */
public record OutlineTopicReviewItem(int topicId, String title, String content) {}
