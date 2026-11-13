package com.ylz.futuremade.meeting.mapper;

import com.ylz.futuremade.meeting.model.entity.TranscriptAiDraftModel;
import org.apache.ibatis.annotations.*;

/** AI 文字稿草稿和来源快照持久化。 */
@Mapper
public interface TranscriptAiDraftMapper {
    @Insert("INSERT INTO mf_transcript_ai_draft(id,meeting_id,actor_id,expected_version,request_hash,sources_json,status) VALUES(#{id},#{meetingId},#{actorId},#{expectedVersion},#{requestHash},#{sourcesJson},#{status})")
    int insert(TranscriptAiDraftModel draft);
    @Select("SELECT id,meeting_id AS meetingId,actor_id AS actorId,expected_version AS expectedVersion,request_hash AS requestHash,sources_json AS sourcesJson,status,ai_call_id AS aiCallId,output_content AS outputContent,created_at AS createdAt FROM mf_transcript_ai_draft WHERE id=#{id}")
    TranscriptAiDraftModel find(String id);
    @Update("UPDATE mf_transcript_ai_draft SET status=#{status},ai_call_id=#{callId},output_content=#{content} WHERE id=#{id} AND status=3")
    int finish(@Param("id") String id,@Param("status") int status,@Param("callId") Long callId,@Param("content") String content);
}
