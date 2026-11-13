package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 单次上下文工具的调用轨迹，内容属于敏感会议数据。 */
@Getter @Setter
public class ExtractionToolTrace {
    /** 模型发起的工具调用 ID。 */ private String callId;
    /** 服务端允许的工具名称。 */ private String name;
    /** 模型传入的 JSON 参数。 */ private String arguments;
    /** 返回给模型的原始 JSON 内容。 */ private String result;
}
