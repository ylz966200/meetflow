package com.ylz.futuremade.meeting.model.dto;

import java.util.List;

/**
 * 归并批次的恢复记录。
 * @param splitRequired 输出是否已截断并需要拆批
 * @param groups 校验通过的分组；等待拆批时为空
 */
public record OutlineGroupingBatchResult(boolean splitRequired, List<TopicGroupingGroup> groups) {}
