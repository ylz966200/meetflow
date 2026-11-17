package com.ylz.futuremade.meeting.mapper;

import com.ylz.futuremade.meeting.model.entity.AiGenerationBatchModel;
import org.apache.ibatis.annotations.*;
import java.util.List;

/** AI 后台生成批次的数据访问与执行租约。 */
@Mapper
public interface AiGenerationBatchMapper {
    /** 保存固定的批次输入。 */
    @Insert("""
        INSERT INTO mf_ai_generation_batch (id, task_id, batch_no, parent_batch_id, status, input_json, input_hash)
        VALUES (#{id}, #{taskId}, #{batchNo}, #{parentBatchId}, #{status}, #{inputJson}, #{inputHash})
        """)
    int insert(AiGenerationBatchModel batch);

    /** 按稳定的批次顺序路径读取批次。 */
    @Select("SELECT id, task_id AS taskId, batch_no AS batchNo, parent_batch_id AS parentBatchId, status, input_json AS inputJson, input_hash AS inputHash, output_json AS outputJson, ai_call_id AS aiCallId, attempt_count AS attemptCount, error_code AS errorCode, lease_owner AS leaseOwner, lease_until AS leaseUntil, attempt_token AS attemptToken, created_at AS createdAt, updated_at AS updatedAt FROM mf_ai_generation_batch WHERE task_id = #{taskId} ORDER BY batch_no")
    List<AiGenerationBatchModel> findByTask(@Param("taskId") String taskId);

    @Select("SELECT id, task_id AS taskId, batch_no AS batchNo, parent_batch_id AS parentBatchId, status, input_json AS inputJson, input_hash AS inputHash, output_json AS outputJson, ai_call_id AS aiCallId, attempt_count AS attemptCount, error_code AS errorCode, lease_owner AS leaseOwner, lease_until AS leaseUntil, attempt_token AS attemptToken, created_at AS createdAt, updated_at AS updatedAt FROM mf_ai_generation_batch WHERE id = #{id}")
    AiGenerationBatchModel findById(@Param("id") String id);

    /** 仅领取待执行或租约已过期的执行中批次；时间以数据库时钟为准。 */
    @Update("""
        UPDATE mf_ai_generation_batch SET status = #{running}, lease_owner = #{owner}, lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, CURRENT_TIMESTAMP(6)),
          attempt_token = #{token}, attempt_count = attempt_count + 1, error_code = NULL
        WHERE id = #{id} AND (status = #{pending} OR (status = #{running} AND lease_until < CURRENT_TIMESTAMP(6)))
        """)
    int claim(@Param("id") String id, @Param("pending") int pending, @Param("running") int running,
              @Param("owner") String owner, @Param("token") String token, @Param("leaseSeconds") int leaseSeconds);

    /** 只有仍持有有效租约的执行尝试可以续租。 */
    @Update("""
        UPDATE mf_ai_generation_batch SET lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, CURRENT_TIMESTAMP(6))
        WHERE id = #{id} AND status = #{running} AND attempt_token = #{token} AND lease_until >= CURRENT_TIMESTAMP(6)
        """)
    int renew(@Param("id") String id, @Param("running") int running, @Param("token") String token, @Param("leaseSeconds") int leaseSeconds);

    /** 执行尝试必须仍持有有效租约，才能保存结果或失败状态。 */
    @Update("""
        UPDATE mf_ai_generation_batch SET status = #{next}, output_json = #{output}, ai_call_id = #{callId},
          error_code = #{error}, lease_owner = NULL, lease_until = NULL, attempt_token = NULL
        WHERE id = #{id} AND status = #{running} AND attempt_token = #{token} AND lease_until >= CURRENT_TIMESTAMP(6)
        """)
    int finish(@Param("id") String id, @Param("running") int running, @Param("token") String token,
               @Param("next") int next, @Param("output") String output, @Param("callId") Long callId, @Param("error") String error);
    /** 列出可领取的批次 ID。 */
    @Select("SELECT id FROM mf_ai_generation_batch WHERE task_id = #{taskId} AND (status = #{pending} OR (status = #{running} AND lease_until < CURRENT_TIMESTAMP(6))) ORDER BY batch_no LIMIT #{limit}")
    List<String> runnable(@Param("taskId") String taskId, @Param("pending") int pending, @Param("running") int running, @Param("limit") int limit);

    /** 将指定任务失败批次重新置为待执行。 */
    @Update("UPDATE mf_ai_generation_batch SET status = #{pending}, error_code = NULL WHERE task_id = #{taskId} AND status = #{failed}")
    int resetFailed(@Param("taskId") String taskId, @Param("failed") int failed, @Param("pending") int pending);
}
