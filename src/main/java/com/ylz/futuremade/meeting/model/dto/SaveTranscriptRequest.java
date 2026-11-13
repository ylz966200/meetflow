package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;


/** 保存会议文字稿请求。 */
@Getter
@Setter
public class SaveTranscriptRequest {
    /** 用户录入或修订的会议文字稿内容。 */
    private String content;

    /** 客户端读取的当前版本；首次保存为 0。 */
    private Integer expectedVersion;

    /** 来源 AI 整理草稿 ID；手工录入文字稿时为空。 */
    private String aiDraftId;

}
