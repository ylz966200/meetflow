package com.ylz.futuremade.meeting.transcript;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** 单个分段 Agent 的上下文工具预算。 */
@Getter @Setter @Component
@ConfigurationProperties(prefix="meetflow.transcript-context")
public class MeetingContextProperties {
    /** 每个分段允许的工具调用次数。 */ private int maxCalls=3;
    /** 单次工具最多返回的发言条数。 */ private int maxUtterances=10;
    /** 单次工具返回的发言正文最大总长度，单位 UTF-16 单元。 */ private int maxChars=3000;
}
