package com.ylz.futuremade.evaluation.mapper;

import com.ylz.futuremade.evaluation.model.entity.EvaluationRun;
import java.util.List;
import org.apache.ibatis.annotations.*;

/** 评测运行记录持久化。 */
@Mapper
public interface EvaluationRunMapper {
    /** 创建一条运行记录。 */
    @Insert("INSERT INTO mf_evaluation_run(id,dataset_version,case_id,actor_id,meeting_id,status) VALUES(#{id},#{datasetVersion},#{caseId},#{actorId},#{meetingId},#{status})")
    int insert(EvaluationRun run);

    /** 保存生成的大纲。 */
    @Update("UPDATE mf_evaluation_run SET outline_json=#{outlineJson} WHERE id=#{id} AND status=#{running}")
    int saveOutline(@Param("id") String id, @Param("outlineJson") String outlineJson, @Param("running") int running);

    /** 保存生成的纪要和行动项。 */
    @Update("UPDATE mf_evaluation_run SET minutes=#{minutes} WHERE id=#{id} AND status=#{running}")
    int saveMinutes(@Param("id") String id, @Param("minutes") String minutes, @Param("running") int running);

    /** 结束运行并记录评审结果或失败阶段。 */
    @Update("UPDATE mf_evaluation_run SET status=#{run.status}, review=#{run.review}, judge_call_id=#{run.judgeCallId}, error_stage=#{run.errorStage} WHERE id=#{run.id} AND status=#{running}")
    int finish(@Param("run") EvaluationRun run, @Param("running") int running);

    /** 查询指定管理员的运行记录。 */
    @Select("SELECT id,dataset_version AS datasetVersion,case_id AS caseId,actor_id AS actorId,meeting_id AS meetingId,status,outline_json AS outlineJson,minutes,review,judge_call_id AS judgeCallId,error_stage AS errorStage,created_at AS createdAt,updated_at AS updatedAt FROM mf_evaluation_run WHERE id=#{id} AND actor_id=#{actor}")
    EvaluationRun find(@Param("id") String id, @Param("actor") long actor);

    /** 查询当前管理员最近的运行记录。 */
    @Select("SELECT id,dataset_version AS datasetVersion,case_id AS caseId,actor_id AS actorId,meeting_id AS meetingId,status,outline_json AS outlineJson,minutes,review,judge_call_id AS judgeCallId,error_stage AS errorStage,created_at AS createdAt,updated_at AS updatedAt FROM mf_evaluation_run WHERE actor_id=#{actor} ORDER BY created_at DESC,id DESC LIMIT 30")
    List<EvaluationRun> recent(@Param("actor") long actor);
}
