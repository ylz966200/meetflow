package com.ylz.futuremade.meeting.mapper;
import com.ylz.futuremade.meeting.model.entity.OutlineSourceModel;
import org.apache.ibatis.annotations.*;
/** 大纲版本来源快照的数据访问。 */
@Mapper
public interface OutlineSourceMapper {
    @Select("SELECT meeting_id AS meetingId,outline_version AS outlineVersion,source_json AS sourceJson FROM mf_outline_source WHERE meeting_id=#{meetingId} AND outline_version=#{version}")
    OutlineSourceModel find(@Param("meetingId") String meetingId,@Param("version") int version);
    @Insert("INSERT INTO mf_outline_source(meeting_id,outline_version,source_json) VALUES(#{meetingId},#{outlineVersion},#{sourceJson})")
    int insert(OutlineSourceModel source);
}
