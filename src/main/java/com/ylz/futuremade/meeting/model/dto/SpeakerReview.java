package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 一次转写任务的说话人校对快照；按识别片段原始顺序排列。
 */
@Getter
@Setter
public class SpeakerReview {
    /**
     * 乐观锁版本号；未保存时为 0。
     */
    private Integer version;
    /**
     * 每个片段的人工归属，顺序和数量必须与该任务片段一致。
     */
    private List<SpeakerAttribution> assignments;
}
