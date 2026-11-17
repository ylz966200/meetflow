package com.ylz.futuremade.meeting.model.dto;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;
/** 归并输入中的候选信息及其分段编号。 */
@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class TopicCandidate extends ChunkExtractionItem implements TopicGroupingInput {
    /** 同一次归并内的候选信息编号。 */
    private String id;
    /** 候选信息所属分段序号，从一开始。 */
    private Integer chunkIndex;
}
