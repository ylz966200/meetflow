package com.ylz.futuremade.meeting.model.dto;

import java.util.List;

/**
 * 大纲归并某一阶段的已校验结果。
 *
 * @param groups          已展开为原候选编号的主题组
 * @param needsCrossMerge 是否还需继续跨批归并
 * @param partialGrouping 是否因拆分或预算限制需要人工核对
 * @param calls           该阶段及此前阶段的归并调用预算消耗
 * @param lastCallId      最近一次对应的 AI 调用记录 ID
 * @param revisionCount   已完成的审核修正次数；旧阶段结果为空
 */
public record OutlineGroupingProgress(List<TopicGroupingGroup> groups, boolean needsCrossMerge,
                                      boolean partialGrouping, int calls, Long lastCallId, Integer revisionCount) {
    /** 普通归并阶段尚未进入审核修正循环。 */
    public OutlineGroupingProgress(List<TopicGroupingGroup> groups, boolean needsCrossMerge,
            boolean partialGrouping, int calls, Long lastCallId) {
        this(groups, needsCrossMerge, partialGrouping, calls, lastCallId, 0);
    }
}
