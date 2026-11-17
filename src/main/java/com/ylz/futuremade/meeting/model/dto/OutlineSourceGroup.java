package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.util.List;
/** 一条大纲和对应候选信息编号。 */
@Getter
@Setter
public class OutlineSourceGroup {
    /** 大纲标题。 */
    private String title;
    /** 该条大纲关联的候选信息编号。 */
    private List<String> itemIds;
}
