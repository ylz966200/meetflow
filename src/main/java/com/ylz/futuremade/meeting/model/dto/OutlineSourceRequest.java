package com.ylz.futuremade.meeting.model.dto;

import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 查询指定大纲版本中的候选信息和来源。 */
@Getter @Setter
public class OutlineSourceRequest {
    /** 大纲版本号。 */ private Integer version;
    /** 需要查看的候选信息编号。 */ private List<String> itemIds;
}
