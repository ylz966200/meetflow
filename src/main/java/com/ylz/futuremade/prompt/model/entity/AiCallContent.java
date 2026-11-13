package com.ylz.futuremade.prompt.model.entity;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** AI 调用输入输出内容表 mf_ai_call_content 对应的数据模型，内容属于敏感数据。 */
@Getter @Setter
public class AiCallContent {
    /** 关联的 AI 调用记录 ID。 */ private Long callId;
    /** 发送给模型的系统提示词原文。 */ private String systemContent;
    /** 发送给模型的用户消息原文，可能包含会议敏感信息。 */ private String userContent;
    /** 模型返回的原始文本，未返回时为空。 */ private String outputContent;
    /** 上下文工具调用和返回结果 JSON，可能包含敏感会议内容。 */ private String toolTrace;
    /** 输入内容记录时间。 */ private LocalDateTime createdAt;
}
