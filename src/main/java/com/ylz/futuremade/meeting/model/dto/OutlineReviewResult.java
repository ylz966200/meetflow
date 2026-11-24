package com.ylz.futuremade.meeting.model.dto;
import java.util.List;
/**
 * 审核 Agent 对实际提供候选的判断，不代表检查了完整文字稿。
 * @param issues 需要人工核对的问题列表，空列表表示本批未发现明显问题
 */
public record OutlineReviewResult(List<OutlineReviewIssue> issues) {}
