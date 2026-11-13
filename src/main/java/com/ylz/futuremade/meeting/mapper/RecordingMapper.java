package com.ylz.futuremade.meeting.mapper;
import com.ylz.futuremade.meeting.model.entity.RecordingModel;
import org.apache.ibatis.annotations.*;
/** 会议录音元数据持久化。 */
@Mapper
public interface RecordingMapper {
    @Insert("INSERT INTO mf_recording(id,meeting_id,object_key,original_name,file_size,format,status,created_by) VALUES(#{id},#{meetingId},#{objectKey},#{originalName},#{fileSize},#{format},#{status},#{createdBy})")
    int insert(RecordingModel record);
    @Update("UPDATE mf_recording SET status=#{status},transcription_task_id=#{taskId} WHERE id=#{id}")
    int update(@Param("id") String id,@Param("status") Integer status,@Param("taskId") String taskId);
    @Select("SELECT id,meeting_id AS meetingId,object_key AS objectKey,original_name AS originalName,file_size AS fileSize,format,status,transcription_task_id AS transcriptionTaskId,created_by AS createdBy,created_at AS createdAt,updated_at AS updatedAt FROM mf_recording WHERE meeting_id=#{meetingId} AND id=#{id}")
    RecordingModel find(@Param("meetingId") String meetingId,@Param("id") String id);
    @Select("SELECT id,meeting_id AS meetingId,object_key AS objectKey,original_name AS originalName,file_size AS fileSize,format,status,transcription_task_id AS transcriptionTaskId,created_by AS createdBy,created_at AS createdAt,updated_at AS updatedAt FROM mf_recording WHERE meeting_id=#{meetingId} AND status IN (9,8,11,12) ORDER BY created_at DESC,id DESC LIMIT 1")
    RecordingModel latest(String meetingId);
    /** 分页读取会议录音及最新任务状态，包含仅保留元数据的已删除录音。 */
    @Select("SELECT r.id,r.meeting_id AS meetingId,r.object_key AS objectKey,r.original_name AS originalName,r.file_size AS fileSize,r.format,r.status,r.transcription_task_id AS transcriptionTaskId,r.created_by AS createdBy,r.created_at AS createdAt,r.updated_at AS updatedAt,t.status AS transcriptionStatus FROM mf_recording r LEFT JOIN mf_transcription_task t ON t.id=r.transcription_task_id AND t.meeting_id=r.meeting_id WHERE r.meeting_id=#{meetingId} AND r.status IN (9,8,11,12,13) ORDER BY r.created_at DESC,r.id DESC LIMIT #{limit} OFFSET #{offset}")
    java.util.List<RecordingModel> list(@Param("meetingId") String meetingId,@Param("limit") int limit,@Param("offset") long offset);
    @Update("UPDATE mf_recording SET status=8 WHERE id=#{id} AND meeting_id=#{meetingId} AND status=9")
    int claim(@Param("meetingId") String meetingId,@Param("id") String id);
    /** 仅允许空闲录音发起一次新任务；状态不明的任务不允许重复提交。 */
    @Update("UPDATE mf_recording SET status=8,transcription_task_id=#{taskId} WHERE id=#{id} AND meeting_id=#{meetingId} AND (status=9 OR (status=11 AND EXISTS (SELECT 1 FROM mf_transcription_task t WHERE t.id=mf_recording.transcription_task_id AND t.status IN (4,5))))")
    int claimNewTask(@Param("meetingId") String meetingId,@Param("id") String id,@Param("taskId") String taskId);
    /** 防止删除正在提交或等待处理的录音。 */
    @Update("UPDATE mf_recording SET status=12 WHERE meeting_id=#{meetingId} AND id=#{id} AND (status=9 OR (status=11 AND NOT EXISTS (SELECT 1 FROM mf_transcription_task t WHERE t.id=mf_recording.transcription_task_id AND t.status IN (6,7,8))))")
    int claimDeletion(@Param("meetingId") String meetingId,@Param("id") String id);
    /** 清理对象成功后保留元数据，用于追溯历史转写。 */
    @Update("UPDATE mf_recording SET status=13 WHERE meeting_id=#{meetingId} AND id=#{id} AND status=12")
    int deleteClaimed(@Param("meetingId") String meetingId,@Param("id") String id);
}
