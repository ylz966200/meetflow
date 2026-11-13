package com.ylz.futuremade.assistant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.HashSet;
import java.util.List;

@Mapper
public interface ToolPermissionMapper {
    @Select("""
            SELECT t.tool_name
            FROM ai_tool t
            JOIN ai_tool_user_permission p ON p.tool_name = t.tool_name
            WHERE t.enabled = TRUE AND p.username = #{username}
            """)
    HashSet<String> findEnabledToolNames(@Param("username") String username);
}
