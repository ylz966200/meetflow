package com.ylz.futuremade.meeting.model.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * 会议录音转写任务表 mf_transcription_task 对应的实体。
 */
@Getter
@Setter
public class TranscriptionTaskModel {
    /**
     * 转写任务 ID，也是火山请求 ID。
     */
    private String id;
    /**
     * 所属会议 ID。
     */
    private String meetingId;
    /**
     * 来源录音 ID；历史链接转写任务为空。
     */
    private String recordingId;
    /**
     * 提交人用户 ID。
     */
    private Long createdBy;
    /**
     * 任务状态码，参见 BusinessStatus 枚举。
     */
    private Integer status;
    /**
     * 识别片段 JSON，属于敏感会议内容。
     */
    private String segmentsJson;
    /**
     * 火山查询成功时的原始响应正文，属于敏感会议内容；历史任务可能为空。
     */
    private String rawResponse;
    /**
     * 人工校对后的说话人归属 JSON，不覆盖原始识别结果。
     */
    private String speakerReviewJson;
    /**
     * 说话人校对快照版本，用于并发更新校验。
     */
    private Integer speakerReviewVersion;
    /**
     * 任务创建时间。
     */
    private LocalDateTime createdAt;
    /**
     * 任务更新时间。
     */
    private LocalDateTime updatedAt;
    /**
     * 下一次允许查询的时间，为空表示可立即查询。
     */
    private LocalDateTime nextPollAt;
    /**
     * 调度器租约标识，未领取时为空。
     */
    private String leaseToken;
    /**
     * 调度器租约截止时间，单位为数据库本地时间。
     */
    private LocalDateTime leaseUntil;
    /**
     * 自动查询次数，达到上限后停止自动查询。
     */
    private Integer pollCount;
}
