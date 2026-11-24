package com.ylz.futuremade.meeting.model.dto;
import java.util.List;
/**
 * 完整大纲的轻量审核请求，不包含候选和原文编号。
 * @param topics 全部议题的编号、标题和内容摘要
 */
public record OutlineTopicReviewRequest(List<OutlineTopicReviewItem> topics) {}
