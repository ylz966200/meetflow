package com.ylz.futuremade.meeting.mapper;
import com.ylz.futuremade.meeting.model.entity.MinutesVersionModel;
import org.apache.ibatis.annotations.*;

/** 会议纪要版本持久化。 */
@Mapper
public interface MinutesMapper {
    @Select("SELECT COALESCE(MAX(version),0) FROM mf_minutes_version WHERE meeting_id=#{meetingId}")
    int latestVersion(String meetingId);
    @Select("SELECT meeting_id AS meetingId,version,outline_version AS outlineVersion,transcript_version AS transcriptVersion,status,content,structured_json AS structuredJson,created_by AS createdBy,created_at AS createdAt FROM mf_minutes_version WHERE meeting_id=#{meetingId} AND version=#{version}")
    MinutesVersionModel find(@Param("meetingId") String meetingId,@Param("version") int version);
    @Insert("INSERT INTO mf_minutes_version(meeting_id,version,outline_version,transcript_version,status,content,structured_json,created_by) VALUES(#{meetingId},#{version},#{outlineVersion},#{transcriptVersion},#{status},#{content},#{structuredJson},#{createdBy})")
    int insert(MinutesVersionModel value);
}
