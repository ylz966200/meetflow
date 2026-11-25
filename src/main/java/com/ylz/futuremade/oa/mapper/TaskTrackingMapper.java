package com.ylz.futuremade.oa.mapper;
import com.ylz.futuremade.oa.model.entity.TaskTrackingLink;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
/** 待办与后续会议来源的确认关联。 */
@Mapper
public interface TaskTrackingMapper {
    /** 保存用户确认的关联。 */
    @Insert("INSERT INTO mf_oa_task_tracking_link(id,task_id,source_meeting_id,minutes_version,section_index,created_by) VALUES(#{id},#{taskId},#{sourceMeetingId},#{minutesVersion},#{sectionIndex},#{createdBy})")
    int insert(TaskTrackingLink link);
    /** 按待办限制撤销关联，避免删除其他待办记录。 */
    @Delete("DELETE FROM mf_oa_task_tracking_link WHERE task_id=#{taskId} AND id=#{id}")
    int delete(@Param("taskId") String taskId, @Param("id") String id);
    /** 按确认时间查询关联。 */
    @Select("SELECT id,task_id AS taskId,source_meeting_id AS sourceMeetingId,minutes_version AS minutesVersion,section_index AS sectionIndex,created_by AS createdBy,created_at AS createdAt FROM mf_oa_task_tracking_link WHERE task_id=#{taskId} ORDER BY created_at,id")
    List<TaskTrackingLink> list(@Param("taskId") String taskId);
}
