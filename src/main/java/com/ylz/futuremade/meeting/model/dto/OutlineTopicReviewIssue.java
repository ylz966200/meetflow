package com.ylz.futuremade.meeting.model.dto;
import java.util.List;
/**
 * 整体审核发现的议题问题。
 * @param topicIds 本次输入中需要核对的议题编号，至少一个
 * @param reason 核对原因，最多1000字符
 */
public record OutlineTopicReviewIssue(List<Integer> topicIds, String reason) {}
