package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 纪要生成时的一条已确认大纲及其候选信息。 */
@Getter @Setter
public class MinutesOutlineInput {
    /** 大纲在确认版本中的序号，从一开始。 */ private int outlineId;
    /** 已确认的大纲标题。 */ private String title;
    /** 此大纲关联的分段提取内容及来源编号。 */ private List<TopicCandidate> candidates;
}
