package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;

/** 生成或保存会议大纲时使用的文字稿和大纲版本信息。 */
@Getter
@Setter
public class OutlineVersionRequest {
    /** 本次大纲所依据的文字稿版本号，从 1 开始。 */
    private Integer transcriptVersion;

    /** 客户端已读取的大纲最新版本号；不存在大纲时为 0，用于防止并发覆盖。 */
    private Integer expectedVersion;
}
