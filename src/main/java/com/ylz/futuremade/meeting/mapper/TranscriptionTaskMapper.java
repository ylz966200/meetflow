package com.ylz.futuremade.meeting.mapper;

import com.ylz.futuremade.meeting.model.entity.TranscriptionTaskModel;
import com.ylz.futuremade.meeting.model.dto.CompletedTranscriptionView;
import com.ylz.futuremade.meeting.model.dto.TranscriptionHistoryView;
import org.apache.ibatis.annotations.*;

/**
 * 录音转写任务持久化。
 */
@Mapper
public interface TranscriptionTaskMapper {
    @Insert("INSERT INTO mf_transcription_task(id,meeting_id,recording_id,created_by,status) VALUES(#{id},#{meetingId},#{recordingId},#{createdBy},#{status})")
    int insert(TranscriptionTaskModel task);

    @Select("SELECT id,meeting_id AS meetingId,recording_id AS recordingId,created_by AS createdBy,status,segments_json AS segmentsJson,speaker_review_json AS speakerReviewJson,speaker_review_version AS speakerReviewVersion,created_at AS createdAt,updated_at AS updatedAt,poll_count AS pollCount FROM mf_transcription_task WHERE id=#{id} AND meeting_id=#{meetingId}")
    TranscriptionTaskModel find(@Param("meetingId") String meetingId, @Param("id") String id);

    @Select("SELECT id,meeting_id AS meetingId,created_by AS createdBy,status,segments_json AS segmentsJson,created_at AS createdAt,updated_at AS updatedAt,poll_count AS pollCount FROM mf_transcription_task WHERE meeting_id=#{meetingId} ORDER BY created_at DESC,id DESC LIMIT 1")
    TranscriptionTaskModel latest(String meetingId);

    /**
     * 分页列出会议中成功的任务；链接任务没有关联上传录音。
     */
    @Select("SELECT t.id,t.created_at AS createdAt,r.original_name AS originalName FROM mf_transcription_task t LEFT JOIN mf_recording r ON r.id=t.recording_id AND r.meeting_id=t.meeting_id WHERE t.meeting_id=#{meetingId} AND t.status=#{status} ORDER BY t.created_at DESC,t.id DESC LIMIT #{limit} OFFSET #{offset}")
    java.util.List<CompletedTranscriptionView> completed(@Param("meetingId") String meetingId, @Param("status") Integer status, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 按录音查询转写历史；不读取原始响应和识别正文。
     */
    @Select("SELECT id,status,created_at AS createdAt FROM mf_transcription_task WHERE meeting_id=#{meetingId} AND recording_id=#{recordingId} ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    java.util.List<TranscriptionHistoryView> history(@Param("meetingId") String meetingId, @Param("recordingId") String recordingId, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 旧链接提交和无法恢复录音关联的历史任务。
     */
    @Select("SELECT id,status,created_at AS createdAt FROM mf_transcription_task WHERE meeting_id=#{meetingId} AND recording_id IS NULL ORDER BY created_at DESC,id DESC LIMIT #{limit} OFFSET #{offset}")
    java.util.List<TranscriptionHistoryView> unlinked(@Param("meetingId") String meetingId, @Param("limit") int limit, @Param("offset") long offset);

    @Update("UPDATE mf_transcription_task SET status=#{status},segments_json=#{segments} WHERE id=#{id} AND status IN (8,6,7)")
    int update(@Param("id") String id, @Param("status") Integer status, @Param("segments") String segments);

    /**
     * 原子保存已完成任务的解析结果和火山原始响应，防止并发覆盖完成状态。
     */
    @Update("UPDATE mf_transcription_task SET status=#{status},segments_json=#{segments},raw_response=#{rawResponse} WHERE id=#{id} AND status IN (8,6,7)")
    int complete(@Param("id") String id, @Param("status") Integer status, @Param("segments") String segments, @Param("rawResponse") String rawResponse);

    /**
     * 保存人工校对快照并校验版本，避免覆盖其他编辑者的修改。
     */
    @Update("UPDATE mf_transcription_task SET speaker_review_json=#{review},speaker_review_version=speaker_review_version+1 WHERE meeting_id=#{meetingId} AND id=#{id} AND status=4 AND speaker_review_version=#{expectedVersion}")
    int updateSpeakerReview(@Param("meetingId") String meetingId, @Param("id") String id, @Param("review") String review, @Param("expectedVersion") int expectedVersion);

    @Select("SELECT id,meeting_id AS meetingId,created_by AS createdBy,status,segments_json AS segmentsJson,created_at AS createdAt,updated_at AS updatedAt FROM mf_transcription_task WHERE (status IN (6,7) OR (status=8 AND created_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 MINUTE))) AND poll_count < #{maxAttempts} AND (next_poll_at IS NULL OR next_poll_at <= CURRENT_TIMESTAMP) AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP) ORDER BY created_at,id LIMIT #{limit}")
    java.util.List<TranscriptionTaskModel> due(@Param("limit") int limit, @Param("maxAttempts") int maxAttempts);

    @Update("UPDATE mf_transcription_task SET lease_token=#{token},lease_until=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{seconds} SECOND) WHERE id=#{id} AND (status IN (6,7) OR (status=8 AND created_at < DATE_SUB(CURRENT_TIMESTAMP, INTERVAL 2 MINUTE))) AND (next_poll_at IS NULL OR next_poll_at <= CURRENT_TIMESTAMP) AND (lease_until IS NULL OR lease_until < CURRENT_TIMESTAMP)")
    int claim(@Param("id") String id, @Param("token") String token, @Param("seconds") int seconds);

    @Update("UPDATE mf_transcription_task SET next_poll_at=DATE_ADD(CURRENT_TIMESTAMP, INTERVAL #{seconds} SECOND),poll_count=poll_count+1,lease_token=NULL,lease_until=NULL WHERE id=#{id} AND lease_token=#{token}")
    int release(@Param("id") String id, @Param("token") String token, @Param("seconds") int seconds);
}
