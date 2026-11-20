package com.ylz.futuremade.oa.mapper;

import com.ylz.futuremade.oa.model.dto.OaTaskView;
import com.ylz.futuremade.oa.model.entity.OaTask;
import com.ylz.futuremade.oa.model.entity.OaTaskAudit;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/** 本地会议待办持久化。 */
@Mapper
public interface OaTaskMapper {
    /** 写入新待办；来源唯一约束防止重复创建。 */
    @Insert("INSERT INTO mf_oa_task(id,meeting_id,minutes_version,action_index,content,assignee_id,due_date,status,created_by) VALUES(#{id},#{meetingId},#{minutesVersion},#{actionIndex},#{content},#{assigneeId},#{dueDate},#{status},#{createdBy})")
    int insert(OaTask task);
    /** 查询会议的待办，包括历史纪要版本。 */
    @Select("SELECT t.id,t.meeting_id AS meetingId,t.minutes_version AS minutesVersion,t.action_index AS actionIndex,t.content,t.assignee_id AS assigneeId,t.due_date AS dueDate,t.status,t.created_by AS createdBy,t.created_at AS createdAt,t.updated_at AS updatedAt,u.username AS assigneeUsername FROM mf_oa_task t JOIN mf_user u ON u.id=t.assignee_id WHERE t.meeting_id=#{meetingId} ORDER BY t.created_at DESC,t.id DESC")
    List<OaTaskView> list(@Param("meetingId") String meetingId);
    /** 根据会议及编号读取待办。 */
    @Select("SELECT id,meeting_id AS meetingId,minutes_version AS minutesVersion,action_index AS actionIndex,content,assignee_id AS assigneeId,due_date AS dueDate,status,created_by AS createdBy,created_at AS createdAt,updated_at AS updatedAt FROM mf_oa_task WHERE meeting_id=#{meetingId} AND id=#{id}")
    OaTask find(@Param("meetingId") String meetingId, @Param("id") String id);
    /** 根据原状态原子更新，避免覆盖并发修改。 */
    @Update("UPDATE mf_oa_task SET status=#{status} WHERE meeting_id=#{meetingId} AND id=#{id} AND status=#{expected}")
    int changeStatus(@Param("meetingId") String meetingId, @Param("id") String id, @Param("expected") int expected, @Param("status") int status);
    /** 保存待办创建或状态变化的操作记录。 */
    @Insert("INSERT INTO mf_oa_task_audit(id,task_id,actor_id,previous_status,status) VALUES(#{id},#{taskId},#{actorId},#{previousStatus},#{status})")
    int audit(OaTaskAudit audit);
    /** 查询待办状态变更历史。 */
    @Select("SELECT id,task_id AS taskId,actor_id AS actorId,previous_status AS previousStatus,status,created_at AS createdAt FROM mf_oa_task_audit WHERE task_id=#{taskId} ORDER BY created_at,id")
    List<OaTaskAudit> history(@Param("taskId") String taskId);
}
