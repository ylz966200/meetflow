package com.ylz.futuremade.meeting.mapper;

import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;

@Mapper
public interface OutlineMapper {
    @Select("SELECT COALESCE(MAX(version),0) FROM mf_outline_version WHERE meeting_id=#{id}")
    int latestVersion(String id);

    @Select("SELECT version, transcript_version AS transcriptVersion, status, items_json AS itemsJson, created_at AS createdAt FROM mf_outline_version WHERE meeting_id=#{id} AND version=#{version}")
    Outline find(@Param("id") String id, @Param("version") int version);

    @Insert("INSERT INTO mf_outline_version (meeting_id,version,transcript_version,status,items_json,created_by) VALUES (#{id},#{version},#{transcriptVersion},#{status},#{itemsJson},#{actor})")
    int insert(@Param("id") String id, @Param("version") int version, @Param("transcriptVersion") int transcriptVersion, @Param("status") Integer status, @Param("itemsJson") String itemsJson, @Param("actor") long actor);

    /**
     * 数据库中的大纲版本。
     *
     * @param version           大纲版本号
     * @param transcriptVersion 关联的文字稿版本号
     * @param status            大纲状态：草稿或已确认
     * @param itemsJson         按顺序保存的大纲条目 JSON
     * @param createdAt         版本创建时间
     */
    record Outline(int version, int transcriptVersion, Integer status, String itemsJson, LocalDateTime createdAt) {
    }
}
