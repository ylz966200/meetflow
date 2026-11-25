package com.ylz.futuremade.oa.model.dto;
import java.util.List;
import com.ylz.futuremade.knowledge.model.dto.KnowledgeMeetingSelection;
import lombok.Getter;
import lombok.Setter;
/** 手动选择用于追踪的后续会议。 */
@Getter @Setter
public class TaskTrackingRequest {
    /** 需要研究的会议及可选的纪要版本。 */
    private List<KnowledgeMeetingSelection> meetings;
}
