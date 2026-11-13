package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
/** 单分段提取请求。 */
@Getter @Setter
public class ChunkExtractionRequest {
    /** 客户端查看分段时的说话人校对版本，用于检测来源变化。 */ private Integer speakerReviewVersion;
}
