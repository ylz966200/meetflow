package com.ylz.futuremade.meeting.transcript;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 文字分段规则；字符长度按 Java UTF-16 单元计数。
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "meetflow.transcript-chunk")
public class ChunkProperties {
    /**
     * 正文目标长度，单位 UTF-16 单元。
     */
    private int targetChars = 6000;
    /**
     * 正文硬上限，单位 UTF-16 单元。
     */
    private int maxChars = 8000;
    /**
     * 最多附带的上一段末尾发言数。
     */
    private int contextUtterances = 5;
    /**
     * 参考上文文字总长度上限，单位 UTF-16 单元。
     */
    private int contextChars = 500;
}
