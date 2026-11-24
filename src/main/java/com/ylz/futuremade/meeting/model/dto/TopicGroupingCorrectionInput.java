package com.ylz.futuremade.meeting.model.dto;

import java.util.List;

/**
 * 归并输出无效后的纠错输入。
 *
 * @param instruction 固定纠错要求，不包含模型错误输出或凭证
 * @param candidates  本次需要重新归并的完整输入，编号必须恰好覆盖一次
 */
public record TopicGroupingCorrectionInput(String instruction, List<TopicGroupingInput> candidates) {
}
