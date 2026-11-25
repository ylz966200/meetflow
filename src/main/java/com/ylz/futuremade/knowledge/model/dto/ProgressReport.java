package com.ylz.futuremade.knowledge.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 单次研究的结构化模型输出。 */
@Getter @Setter
public class ProgressReport {
    /** 整体结论，未找到时明确说明。 */ private String summary;
    /** 相关候选；无相关进展时为空列表。 */ private java.util.List<ProgressFinding> candidates;
}
