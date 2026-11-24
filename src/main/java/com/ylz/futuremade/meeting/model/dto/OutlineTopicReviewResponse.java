package com.ylz.futuremade.meeting.model.dto;
import java.util.List;
/**
 * 按议题编号定位的整体审核结果。
 * @param issues 发现的问题，无问题时为空列表
 */
public record OutlineTopicReviewResponse(List<OutlineTopicReviewIssue> issues) {}
