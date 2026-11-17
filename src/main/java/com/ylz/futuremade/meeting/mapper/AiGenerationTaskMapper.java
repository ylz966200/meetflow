package com.ylz.futuremade.meeting.mapper;

import com.ylz.futuremade.meeting.model.entity.AiGenerationTaskModel;
import org.apache.ibatis.annotations.*;

/** AI 后台生成任务的数据访问。 */
@Mapper
public interface AiGenerationTaskMapper {
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

    /** 查询可继续处理的任务。 */
    @Select("SELECT id FROM mf_ai_generation_task WHERE task_type = #{type} AND status IN (#{pending}, #{running}) ORDER BY created_at LIMIT #{limit}")
    java.util.List<String> runnable(@Param("type") String type, @Param("pending") int pending, @Param("running") int running, @Param("limit") int limit);
}
