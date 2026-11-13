package com.ylz.futuremade.meeting.model.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

/** 成功转写任务的列表摘要，不返回录音地址和文字稿正文。 */
@Getter
@Setter
public class CompletedTranscriptionView {
    /** 转写任务 ID，用于读取对应的识别结果。 */
    private String id;
    /** 上传录音的原始文件名；链接转写时为空。 */
    private String originalName;
    /** 转写任务创建时间。 */
    private LocalDateTime createdAt;
}
