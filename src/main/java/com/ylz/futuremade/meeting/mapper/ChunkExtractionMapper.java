package com.ylz.futuremade.meeting.mapper;
import com.ylz.futuremade.meeting.model.entity.ChunkExtractionModel;
import org.apache.ibatis.annotations.*;
/** 单分段 AI 提取请求和结果持久化。 */
@Mapper
public interface ChunkExtractionMapper {
    @Insert("INSERT INTO mf_chunk_extraction(id,meeting_id,task_id,chunk_index,speaker_review_version,actor_id,status,chunk_fingerprint) VALUES(#{id},#{meetingId},#{taskId},#{chunkIndex},#{speakerReviewVersion},#{actorId},#{status},#{chunkFingerprint})")
    int insert(ChunkExtractionModel value);
    @Select("SELECT id,meeting_id AS meetingId,task_id AS taskId,chunk_index AS chunkIndex,speaker_review_version AS speakerReviewVersion,actor_id AS actorId,status,ai_call_id AS aiCallId,result_json AS resultJson,created_at AS createdAt FROM mf_chunk_extraction WHERE id=#{id}")
    ChunkExtractionModel find(String id);
    /** 按分段读取同一用户成功的历史记录，最近的模型调用排在前面。 */
    @Select("SELECT chunk_index AS chunkIndex, chunk_fingerprint AS chunkFingerprint, result_json AS resultJson FROM mf_chunk_extraction WHERE meeting_id=#{meetingId} AND task_id=#{taskId} AND actor_id=#{actorId} AND status=#{status} AND chunk_fingerprint IS NOT NULL ORDER BY chunk_index, ai_call_id DESC")
    java.util.List<ChunkExtractionModel> completed(@Param("meetingId") String meetingId, @Param("taskId") String taskId, @Param("actorId") long actorId, @Param("status") int status);
    @Update("UPDATE mf_chunk_extraction SET status=#{status},ai_call_id=#{callId},result_json=#{result} WHERE id=#{id} AND status=3")
    int finish(@Param("id") String id,@Param("status") int status,@Param("callId") Long callId,@Param("result") String result);
}
