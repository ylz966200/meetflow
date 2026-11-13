package com.ylz.futuremade.meeting.mapper;
import com.ylz.futuremade.meeting.model.entity.GenerationRequestModel;
import org.apache.ibatis.annotations.*;

/** AI 生成请求去重记录的数据访问。 */
@Mapper
public interface GenerationRequestMapper {
    @Insert("INSERT INTO mf_generation_request(request_key,scenario,meeting_id,actor_id,source_version,expected_version,status) VALUES(#{requestKey},#{scenario},#{meetingId},#{actorId},#{sourceVersion},#{expectedVersion},3)")
    int insert(GenerationRequestModel value);
    @Select("SELECT request_key AS requestKey,scenario,meeting_id AS meetingId,actor_id AS actorId,source_version AS sourceVersion,expected_version AS expectedVersion,status,result_version AS resultVersion FROM mf_generation_request WHERE request_key=#{key}")
    GenerationRequestModel find(String key);
    @Update("UPDATE mf_generation_request SET status=#{status},result_version=#{version} WHERE request_key=#{key} AND status=3")
    int finish(@Param("key") String key,@Param("status") Integer status,@Param("version") Integer version);
}
