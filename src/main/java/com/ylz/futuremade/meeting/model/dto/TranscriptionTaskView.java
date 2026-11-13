package com.ylz.futuremade.meeting.model.dto;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.List;
import com.ylz.futuremade.meeting.transcript.TranscriptSegment;
/** 转写任务状态和已识别片段，不包含音频链接。 */
@Getter @Setter
public class TranscriptionTaskView {
    /** 任务 ID。 */ private String id;
    /** 任务状态码，参见 BusinessStatus 枚举。 */ private Integer status;
    /** 说话人标签及识别文本，尚未完成时为空列表。 */ private List<TranscriptSegment> segments;
    /** 任务创建时间。 */ private LocalDateTime createdAt;
    /** 任务更新时间。 */ private LocalDateTime updatedAt;
    /** 自动查询次数达到上限，仍可手动查询。 */ private Boolean pollingStopped;
}
