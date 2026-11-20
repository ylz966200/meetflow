package com.ylz.futuremade.evaluation.mapper;
import com.ylz.futuremade.evaluation.model.entity.KnowledgeEvaluationRun;
import java.util.List;
import org.apache.ibatis.annotations.*;
/** 知识问答评测记录持久化。 */
@Mapper
public interface KnowledgeEvaluationRunMapper {
    /** 创建运行快照。 */
    @Insert("INSERT INTO mf_knowledge_evaluation_run(id,dataset_version,case_id,actor_id,meeting_ids,status,sample_json) VALUES(#{id},#{datasetVersion},#{caseId},#{actorId},#{meetingIds},#{status},#{sampleJson})")
    int insert(KnowledgeEvaluationRun run);
    /** 保存问答结果和来源检查。 */
    @Update("UPDATE mf_knowledge_evaluation_run SET result_json=#{resultJson},source_checks_json=#{sourceChecksJson},request_key=#{requestKey} WHERE id=#{id} AND status=3")
    int saveResult(KnowledgeEvaluationRun run);
    /** 保存终态及评审意见。 */
    @Update("UPDATE mf_knowledge_evaluation_run SET status=#{status},error_stage=#{errorStage},review=#{review},judge_call_id=#{judgeCallId} WHERE id=#{id} AND status=3")
    int finish(KnowledgeEvaluationRun run);
    /** 只允许抢占处于评审失败状态的运行，避免并发重复评审。 */
    @Update("UPDATE mf_knowledge_evaluation_run SET status=3,error_stage=NULL WHERE id=#{id} AND actor_id=#{actor} AND status=5 AND error_stage='JUDGE'")
    int claimReviewRetry(@Param("id") String id,@Param("actor") long actor);
    /** 读取管理员的运行记录。 */
    @Select("SELECT id,dataset_version AS datasetVersion,case_id AS caseId,actor_id AS actorId,meeting_ids AS meetingIds,status,error_stage AS errorStage,sample_json AS sampleJson,result_json AS resultJson,source_checks_json AS sourceChecksJson,request_key AS requestKey,review,judge_call_id AS judgeCallId,manual_review_status AS manualReviewStatus,manual_review_note AS manualReviewNote,created_at AS createdAt,updated_at AS updatedAt FROM mf_knowledge_evaluation_run WHERE id=#{id} AND actor_id=#{actor}")
    KnowledgeEvaluationRun find(@Param("id") String id,@Param("actor") long actor);
    /** 获取当前管理员最近的记录。 */
    @Select("SELECT id,dataset_version AS datasetVersion,case_id AS caseId,actor_id AS actorId,meeting_ids AS meetingIds,status,error_stage AS errorStage,request_key AS requestKey,manual_review_status AS manualReviewStatus,created_at AS createdAt,updated_at AS updatedAt FROM mf_knowledge_evaluation_run WHERE actor_id=#{actor} ORDER BY created_at DESC,id DESC LIMIT 30")
    List<KnowledgeEvaluationRun> recent(long actor);
    /** 保存有权查看且已有问答结果的记录的人工复核。 */
    @Update("UPDATE mf_knowledge_evaluation_run SET manual_review_status=#{status},manual_review_note=#{note} WHERE id=#{id} AND actor_id=#{actor} AND result_json IS NOT NULL AND status<>3")
    int review(@Param("id") String id,@Param("actor") long actor,@Param("status") int status,@Param("note") String note);
}
