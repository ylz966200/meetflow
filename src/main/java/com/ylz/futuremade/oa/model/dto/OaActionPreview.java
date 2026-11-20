package com.ylz.futuremade.oa.model.dto;

import com.ylz.futuremade.meeting.model.dto.MinutesActionItem;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

/** 已确认会议纪要中的待办检查结果。 */
@Getter
@Setter
public class OaActionPreview {
    /** 会议编号。 */
    private String meetingId;
    /** 行动项来源纪要版本。 */
    private Integer minutesVersion;
    /** 已确认纪要中的行动项，仍需人工核对。 */
    private List<MinutesActionItem> actions;
}
