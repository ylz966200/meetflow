package com.ylz.futuremade.meeting.mapper;
import com.ylz.futuremade.meeting.model.entity.ChunkSummaryModel;
import org.apache.ibatis.annotations.*;
/** 分段汇总请求和结果的数据访问。 */
@Mapper
public interface ChunkSummaryMapper {
    @Insert("INSERT INTO mf_chunk_summary(id,meeting_id,task_id,actor_id,source_hash,status) VALUES(#{id},#{meetingId},#{taskId},#{actorId},#{sourceHash},#{status})")
    int insert(ChunkSummaryModel record);
    @Select("SELECT id,meeting_id AS meetingId,task_id AS taskId,actor_id AS actorId,source_hash AS sourceHash,status,ai_call_id AS aiCallId,content,created_at AS createdAt FROM mf_chunk_summary WHERE id=#{id}")
    ChunkSummaryModel find(String id);
    @Update("UPDATE mf_chunk_summary SET status=#{status}, ai_call_id=#{callId}, content=#{content} WHERE id=#{id} AND status=3")
    int finish(@Param("id") String id, @Param("status") int status, @Param("callId") Long callId, @Param("content") String content);
}
