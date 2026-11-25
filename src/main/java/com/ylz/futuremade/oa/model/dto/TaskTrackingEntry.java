package com.ylz.futuremade.oa.model.dto;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeSource;
import com.ylz.futuremade.oa.model.entity.TaskTrackingLink;
import lombok.Getter;
import lombok.Setter;
/** 待办的人工确认进展及其可见的纪要来源。 */
@Getter @Setter
public class TaskTrackingEntry {
    /** 用户确认的关联记录。 */ private TaskTrackingLink link;
    /** 已校验权限的纪要条目。 */ private KnowledgeSource source;
}
