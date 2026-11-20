package com.ylz.futuremade.prompt.model.entity;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * AI 模型调用记录表 mf_ai_call 对应的数据模型，不包含会议正文及模型输出。
 */
@Getter
@Setter
public class PromptCall {
    /**
     * 调用记录 ID。
     */
    private Long id;
    /**
     * 业务场景编码。
     */
    private String scenario;
    /**
     * 调用使用的提示词版本主键，对应 mf_prompt_version.id。
     */
    private Long promptVersionId;
    /**
     * 关联会议 ID。
     */
    private String meetingId;
    /** 多会议调用的会议 ID 列表，以逗号分隔。 */
    private String meetingIds;
    /**
     * 关联文字稿版本号；文字稿生成前的整理调用为空。
     */
    private Integer transcriptVersion;
    /**
     * 发起人用户 ID。
     */
    private Long actorId;
    /**
     * 调用状态码，参见 BusinessStatus 枚举。
     */
    private Integer status;
    /**
     * 耗时，单位毫秒。
     */
    private Long durationMs;
    /**
     * 不含模型返回内容的失败分类。
     */
    private String errorCategory;
    /**
     * 调用开始时间。
     */
    private LocalDateTime createdAt;
    /**
     * 生成请求标识，用于关联业务处理结果；历史数据可能为空。
     */
    private String requestKey;
    /** 后台任务的批次 ID，用于查看同一批次各轮模型调用。 */
    private String batchId;
    /**
     * 业务处理状态；历史调用没有关联请求时为空。
     */
    private Integer businessStatus;
    /**
     * 保存成功的产物版本号；尚未保存时为空。
     */
    private Integer resultVersion;
    /**
     * 纪要生成所依据的大纲版本号；大纲生成时为空。
     */
    private Integer outlineVersion;
    /**
     * 模型服务供应商，调用未取得响应时为空。
     */
    private String provider;
    /**
     * 供应商响应中的模型名称。
     */
    private String modelName;
    /**
     * 供应商响应中的请求 ID。
     */
    private String providerRequestId;
    /**
     * 供应商返回的输入 Token 数，未返回时为空。
     */
    private Integer inputTokens;
    /**
     * 供应商返回的输出 Token 数，未返回时为空。
     */
    private Integer outputTokens;
    /**
     * 供应商返回的缓存命中 Token 数，未返回时为空。
     */
    private Integer cachedTokens;

    /**
     * 兼容旧版调用记录接口，返回与 promptVersionId 相同的值。
     */
    @Deprecated
    public Long getVersionId() {
        return promptVersionId;
    }
}
