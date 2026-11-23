package com.ylz.futuremade.meeting.mapper;

import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import org.apache.ibatis.annotations.*;

/** AI 后台生成任务的数据访问。 */
@Mapper
public interface AiGenerationTaskMapper {
    /** 旧大纲任务以此固定编号保存整份归并结果，不能交给新分段流程。 */
    String LEGACY_OUTLINE_GROUP_BATCH = "99999999";
    /** 新大纲调度保持旧流程的批次数据原样。 */
    String NEW_OUTLINE_ONLY = " AND (#{type}<>'MEETING_OUTLINE' OR NOT EXISTS (SELECT 1 FROM mf_ai_generation_batch legacy"
            + " WHERE legacy.task_id=mf_ai_generation_task.id AND legacy.batch_no='" + LEGACY_OUTLINE_GROUP_BATCH + "'))";
    /** 新建任务，幂等键由数据库唯一索引保证。 */
    @Insert("""
        INSERT INTO mf_ai_generation_task
        (id, request_key, meeting_id, task_type, status, actor_id, transcript_version, outline_version,
         expected_result_version, prompt_version_id, model_name, config_json)
        VALUES (#{id}, #{requestKey}, #{meetingId}, #{taskType}, #{status}, #{actorId}, #{transcriptVersion},
         #{outlineVersion}, #{expectedResultVersion}, #{promptVersionId}, #{modelName}, #{configJson})
        """)
    int insert(AiGenerationTaskModel task);

    /** 根据任务 ID 查询。 */
    @Select("SELECT id, request_key AS requestKey, meeting_id AS meetingId, task_type AS taskType, status, actor_id AS actorId, transcript_version AS transcriptVersion, outline_version AS outlineVersion, expected_result_version AS expectedResultVersion, prompt_version_id AS promptVersionId, model_name AS modelName, config_json AS configJson, result_version AS resultVersion, error_code AS errorCode, created_at AS createdAt, started_at AS startedAt, finished_at AS finishedAt, updated_at AS updatedAt FROM mf_ai_generation_task WHERE id = #{id}")
    AiGenerationTaskModel findById(@Param("id") String id);

    @Select("SELECT id, request_key AS requestKey, meeting_id AS meetingId, task_type AS taskType, status, actor_id AS actorId, transcript_version AS transcriptVersion, outline_version AS outlineVersion, expected_result_version AS expectedResultVersion, prompt_version_id AS promptVersionId, model_name AS modelName, config_json AS configJson, result_version AS resultVersion, error_code AS errorCode, created_at AS createdAt, started_at AS startedAt, finished_at AS finishedAt, updated_at AS updatedAt FROM mf_ai_generation_task WHERE id = #{id} FOR UPDATE")
    AiGenerationTaskModel lockById(@Param("id") String id);

    /** 根据幂等键查询既有任务。 */
    @Select("SELECT id, request_key AS requestKey, meeting_id AS meetingId, task_type AS taskType, status, actor_id AS actorId, transcript_version AS transcriptVersion, outline_version AS outlineVersion, expected_result_version AS expectedResultVersion, prompt_version_id AS promptVersionId, model_name AS modelName, config_json AS configJson, result_version AS resultVersion, error_code AS errorCode, created_at AS createdAt, started_at AS startedAt, finished_at AS finishedAt, updated_at AS updatedAt FROM mf_ai_generation_task WHERE request_key = #{key}")
    AiGenerationTaskModel findByRequestKey(@Param("key") String key);

    @Select("SELECT id, request_key AS requestKey, meeting_id AS meetingId, task_type AS taskType, status, actor_id AS actorId, transcript_version AS transcriptVersion, outline_version AS outlineVersion, expected_result_version AS expectedResultVersion, prompt_version_id AS promptVersionId, model_name AS modelName, config_json AS configJson, result_version AS resultVersion, error_code AS errorCode, created_at AS createdAt, started_at AS startedAt, finished_at AS finishedAt, updated_at AS updatedAt FROM mf_ai_generation_task WHERE request_key = #{key} FOR UPDATE")
    AiGenerationTaskModel lockByRequestKey(@Param("key") String key);

    /** 按会议查询最近创建的任务。 */
    @Select("SELECT id, request_key AS requestKey, meeting_id AS meetingId, task_type AS taskType, status, actor_id AS actorId, transcript_version AS transcriptVersion, outline_version AS outlineVersion, expected_result_version AS expectedResultVersion, prompt_version_id AS promptVersionId, model_name AS modelName, config_json AS configJson, result_version AS resultVersion, error_code AS errorCode, created_at AS createdAt, started_at AS startedAt, finished_at AS finishedAt, updated_at AS updatedAt FROM mf_ai_generation_task WHERE meeting_id = #{meetingId} AND task_type = #{taskType} ORDER BY created_at DESC, id DESC LIMIT 1")
    AiGenerationTaskModel findLatest(@Param("meetingId") String meetingId, @Param("taskType") String taskType);

    /** 按旧状态条件更新，避免覆盖并发状态变更。 */
    @Update("UPDATE mf_ai_generation_task SET status = #{next}, error_code = #{error} WHERE id = #{id} AND status = #{expected}")
    int transition(@Param("id") String id, @Param("expected") int expected, @Param("next") int next, @Param("error") String error);
    /** 原子写入已保存纪要的版本。 */
    @Update("UPDATE mf_ai_generation_task SET status = #{succeeded}, result_version = #{version}, finished_at = CURRENT_TIMESTAMP(6) WHERE id = #{id} AND status = #{running}")
    int complete(@Param("id") String id, @Param("running") int running, @Param("succeeded") int succeeded, @Param("version") int version);

    /** 领取指定生成任务的 Graph 执行权；旧版大纲任务仍排除在外。 */
    @Update("UPDATE mf_ai_generation_task SET status=#{running}, started_at=COALESCE(started_at,CURRENT_TIMESTAMP(6)), graph_lease_owner=#{owner}, graph_lease_until=TIMESTAMPADD(SECOND, #{seconds}, CURRENT_TIMESTAMP(6)), graph_attempt_token=#{token} WHERE id=#{id} AND task_type=#{type} AND status IN (#{pending},#{running}) AND (graph_lease_until IS NULL OR graph_lease_until<CURRENT_TIMESTAMP(6))" + NEW_OUTLINE_ONLY)
    int claimGraph(@Param("id") String id, @Param("type") String type, @Param("pending") int pending, @Param("running") int running, @Param("owner") String owner, @Param("token") String token, @Param("seconds") int seconds);

    /** 只有有效执行者可以续租。 */
    @Update("UPDATE mf_ai_generation_task SET graph_lease_until=TIMESTAMPADD(SECOND, #{seconds}, CURRENT_TIMESTAMP(6)) WHERE id=#{id} AND graph_attempt_token=#{token} AND graph_lease_until>=CURRENT_TIMESTAMP(6)")
    int renewGraph(@Param("id") String id, @Param("token") String token, @Param("seconds") int seconds);

    /** 模型调用前快速验证任务执行权。 */
    @Select("SELECT COUNT(*) FROM mf_ai_generation_task WHERE id=#{id} AND graph_attempt_token=#{token} AND graph_lease_until>=CURRENT_TIMESTAMP(6)")
    int ownsGraph(@Param("id") String id, @Param("token") String token);

    /** 锁定有效租约，用当前读避免提前建立事务的一致性快照。 */
    @Select("SELECT graph_attempt_token FROM mf_ai_generation_task WHERE id=#{id} AND graph_lease_until>=CURRENT_TIMESTAMP(6) FOR UPDATE")
    String lockActiveGraphToken(@Param("id") String id);

    /** 仅持有有效租约的执行者可以标记失败，防止过期执行者覆盖新任务状态。 */
    @Update("UPDATE mf_ai_generation_task SET status=#{next}, error_code=#{error} WHERE id=#{id} AND status=#{running} AND graph_attempt_token=#{token} AND graph_lease_until>=CURRENT_TIMESTAMP(6)")
    int failGraph(@Param("id") String id, @Param("token") String token, @Param("running") int running, @Param("next") int next, @Param("error") String error);

    /** 发现尚未被领取或租约已过期的指定类型生成任务。 */
    @Select("SELECT id FROM mf_ai_generation_task WHERE task_type=#{type} AND status IN (#{pending},#{running}) AND (graph_lease_until IS NULL OR graph_lease_until<CURRENT_TIMESTAMP(6))"
            + NEW_OUTLINE_ONLY + " ORDER BY created_at LIMIT #{limit}")
    java.util.List<String> runnableGraph(@Param("type") String type, @Param("pending") int pending, @Param("running") int running, @Param("limit") int limit);

    /** 仅当前执行者可以释放 Graph 执行权。 */
    @Update("UPDATE mf_ai_generation_task SET graph_lease_owner=NULL, graph_lease_until=NULL, graph_attempt_token=NULL WHERE id=#{id} AND graph_attempt_token=#{token}")
    int releaseGraph(@Param("id") String id, @Param("token") String token);

    /** 读取只含阶段和引用的最新检查点。 */
    @Select("SELECT graph_checkpoint_json FROM mf_ai_generation_task WHERE id=#{id}")
    String outlineCheckpoint(@Param("id") String id);

    /** 用户重试失败批次时，撤销旧执行权并由 Graph 重新读取批次进度。 */
    @Update("UPDATE mf_ai_generation_task SET graph_checkpoint_json=NULL, graph_lease_owner=NULL, graph_lease_until=NULL, graph_attempt_token=NULL WHERE id=#{id}")
    int resetOutlineCheckpoint(@Param("id") String id);

    /** 仅持有有效任务租约的执行者可更新检查点。 */
    @Update("UPDATE mf_ai_generation_task SET graph_checkpoint_json=#{snapshot,jdbcType=VARCHAR} WHERE id=#{id} AND graph_attempt_token=#{token} AND graph_lease_until>=CURRENT_TIMESTAMP(6)")
    int saveOutlineCheckpoint(@Param("id") String id, @Param("token") String token,
                              @Param("snapshot") String snapshot);

    /** 查询可继续处理的任务。 */
    @Select("SELECT id FROM mf_ai_generation_task WHERE task_type = #{type} AND status IN (#{pending}, #{running}) ORDER BY created_at LIMIT #{limit}")
    java.util.List<String> runnable(@Param("type") String type, @Param("pending") int pending, @Param("running") int running, @Param("limit") int limit);
}
