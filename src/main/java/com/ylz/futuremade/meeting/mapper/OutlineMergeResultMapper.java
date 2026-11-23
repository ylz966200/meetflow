package com.ylz.futuremade.meeting.mapper;

import com.ylz.futuremade.meeting.model.entity.OutlineMergeResultModel;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 大纲归并已校验结果的数据访问。 */
@Mapper
public interface OutlineMergeResultMapper {
    /** 根据固定输入摘要读取已保存结果。 */
    @Select("SELECT task_id AS taskId, input_hash AS inputHash, output_json AS outputJson, ai_call_id AS aiCallId, created_at AS createdAt FROM mf_outline_merge_result WHERE task_id=#{taskId} AND input_hash=#{inputHash}")
    OutlineMergeResultModel find(@Param("taskId") String taskId, @Param("inputHash") String inputHash);

    /** 调用方在同一事务内锁定任务行并确认执行权。 */
    @Insert("""
        INSERT INTO mf_outline_merge_result (task_id, input_hash, output_json, ai_call_id)
        VALUES (#{taskId}, #{inputHash}, #{outputJson}, #{aiCallId})
        """)
    int insert(OutlineMergeResultModel result);
}
