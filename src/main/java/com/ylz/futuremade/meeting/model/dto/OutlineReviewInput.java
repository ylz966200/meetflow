package com.ylz.futuremade.meeting.model.dto;
import java.util.List;
/**
 * 整体或局部大纲审核输入。
 * @param title 审核对象说明；单主题详查时为主题名称
 * @param candidates 实际提供的候选；摘要模式的内容可能缩短
 * @param groups 全部主题及其候选关联；局部详查时为空
 * @param summaryOnly 是否为摘要模式，不能声称已读取完整候选或文字稿
 */
public record OutlineReviewInput(String title, List<TopicCandidate> candidates,
                                 List<TopicGroupingGroup> groups, boolean summaryOnly) {
    /** 单主题局部详查，提供完整候选。 */
    public OutlineReviewInput(String title, List<TopicCandidate> candidates) {
        this(title, candidates, List.of(), false);
    }
}
