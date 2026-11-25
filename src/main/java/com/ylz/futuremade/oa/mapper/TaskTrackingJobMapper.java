package com.ylz.futuremade.oa.mapper;

import com.ylz.futuremade.oa.model.entity.TaskTrackingJob;

import java.util.List;

import org.apache.ibatis.annotations.*;

/**
 * 单会议追踪任务及执行令牌持久化。
 */
@Mapper
public interface TaskTrackingJobMapper {
    String COLUMNS = "id,run_id AS runId,task_id AS taskId,origin_meeting_id AS originMeetingId,meeting_id AS meetingId,minutes_version AS minutesVersion,actor_id AS actorId,username,status,token,result_json AS resultJson,lease_until AS leaseUntil,created_at AS createdAt";

    /**
     * 建立待执行任务。
     */
    @Insert("INSERT INTO mf_oa_tracking_job(id,run_id,task_id,origin_meeting_id,meeting_id,minutes_version,actor_id,username,status) VALUES(#{id},#{runId},#{taskId},#{originMeetingId},#{meetingId},#{minutesVersion},#{actorId},#{username},1)")
    int insert(TaskTrackingJob job);

    /**
     * 扫描待执行和已过租约的任务。
     */
    @Select("SELECT " + COLUMNS + " FROM mf_oa_tracking_job WHERE status=1 OR (status=2 AND lease_until<NOW()) ORDER BY created_at LIMIT 10")
    List<TaskTrackingJob> runnable();

    /**
     * 查询一个追踪批次。
     */
    @Select("SELECT " + COLUMNS + " FROM mf_oa_tracking_job WHERE task_id=#{taskId} AND run_id=#{runId} AND actor_id=#{actor} ORDER BY created_at,id")
    List<TaskTrackingJob> list(@Param("taskId") String taskId, @Param("runId") String runId, @Param("actor") long actor);

    /**
     * 原子获取租约及新令牌。
     */
    @Update("UPDATE mf_oa_tracking_job SET status=2,token=#{token},lease_until=DATE_ADD(NOW(), INTERVAL #{seconds} SECOND) WHERE id=#{id} AND (status=1 OR (status=2 AND lease_until<NOW()))")
    int claim(@Param("id") String id, @Param("token") String token, @Param("seconds") int seconds);

    /**
     * 只有未过期的执行者可以续租。
     */
    @Update("UPDATE mf_oa_tracking_job SET lease_until=DATE_ADD(NOW(), INTERVAL #{seconds} SECOND) WHERE id=#{id} AND status=2 AND token=#{token} AND lease_until>=NOW()")
    int renew(@Param("id") String id, @Param("token") String token, @Param("seconds") int seconds);

    /**
     * 用令牌保护完成或失败写入。
     */
    @Update("UPDATE mf_oa_tracking_job SET status=#{status},result_json=#{result},token=NULL,lease_until=NULL WHERE id=#{id} AND status=2 AND token=#{token} AND lease_until>=NOW()")
    int finish(@Param("id") String id, @Param("token") String token, @Param("status") int status, @Param("result") String result);

    /**
     * 仅重试本人当前批次失败的任务，复用成功结果。
     */
    @Update("UPDATE mf_oa_tracking_job SET status=1 WHERE task_id=#{taskId} AND run_id=#{runId} AND actor_id=#{actor} AND status=4")
    int retry(@Param("taskId") String taskId, @Param("runId") String runId, @Param("actor") long actor);
}
