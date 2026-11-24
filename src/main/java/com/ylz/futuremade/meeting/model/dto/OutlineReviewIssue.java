package com.ylz.futuremade.meeting.model.dto;
import java.util.List;
/**
 * 大纲归并审核发现的问题。
 * @param candidateIds 问题涉及的已提供候选编号，至少一个
 * @param reason 需要核对的具体原因，最多 1000 字符
 */
public record OutlineReviewIssue(List<String> candidateIds, String reason) {}
