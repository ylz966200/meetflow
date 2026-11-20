package com.ylz.futuremade.evaluation.model.dto;
import java.util.List;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeConversationTurn;
import lombok.Getter;
import lombok.Setter;
/** 单条会议知识问答样本与预期。 */
@Getter @Setter
public class KnowledgeEvaluationCase {
    /** 样本 ID。 */ private String id;
    /** 测试类别。 */ private String category;
    /** 引用的会议简称。 */ private List<String> meetingKeys;
    /** 用户问题。 */ private String question;
    /** 评测追问所需历史对话。 */ private List<KnowledgeConversationTurn> history;
    /** 尚需恢复的历史来源。 */ private KnowledgeEvaluationHistorySource historySource;
    /** 人工预设事实。 */ private List<String> expectedFacts;
    /** 人工预设的来源。 */ private List<KnowledgeEvaluationSource> expectedSources;
    /** 预期回答行为。 */ private String expectedBehavior;
    /** 标准答案审核状态。 */ private String reviewStatus;
}
