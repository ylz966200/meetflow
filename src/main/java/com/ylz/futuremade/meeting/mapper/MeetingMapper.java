package com.ylz.futuremade.meeting.mapper;

import org.apache.ibatis.annotations.*;
import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MeetingMapper {
    @Insert("INSERT INTO mf_meeting (id, owner_id, title, meeting_time) VALUES (#{id}, #{ownerId}, #{title}, #{meetingTime})")
    int insert(@Param("id") String id, @Param("ownerId") long ownerId, @Param("title") String title,
               @Param("meetingTime") LocalDateTime meetingTime);

    @Select("SELECT m.id, m.title, m.meeting_time AS meetingTime, m.created_at AS createdAt FROM mf_meeting m WHERE m.owner_id = #{ownerId} OR EXISTS (SELECT 1 FROM mf_meeting_member mm WHERE mm.meeting_id = m.id AND mm.user_id = #{ownerId}) ORDER BY m.created_at DESC, m.id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Meeting> list(@Param("ownerId") long ownerId, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT m.id, m.title, m.meeting_time AS meetingTime, m.created_at AS createdAt FROM mf_meeting m WHERE m.id = #{id} AND (m.owner_id = #{ownerId} OR EXISTS (SELECT 1 FROM mf_meeting_member mm WHERE mm.meeting_id = m.id AND mm.user_id = #{ownerId}))")
    Meeting find(@Param("id") String id, @Param("ownerId") long ownerId);

    @Select("SELECT owner_id FROM mf_meeting WHERE id = #{id} FOR UPDATE")
    Long lockOwner(@Param("id") String id);

    @Select("SELECT owner_id FROM mf_meeting WHERE id = #{id}")
    Long owner(@Param("id") String id);

    @Select("SELECT member_role FROM mf_meeting_member WHERE meeting_id = #{id} AND user_id = #{userId}")
    String memberRole(@Param("id") String id, @Param("userId") long userId);

    @Insert("INSERT INTO mf_meeting_member (meeting_id, user_id, member_role) VALUES (#{id}, #{userId}, #{role})")
    int addMember(@Param("id") String id, @Param("userId") long userId, @Param("role") String role);

    @Update("UPDATE mf_meeting_member SET member_role = #{role} WHERE meeting_id = #{id} AND user_id = #{userId} AND member_role = #{expected}")
    int changeMemberRole(@Param("id") String id, @Param("userId") long userId, @Param("role") String role, @Param("expected") String expected);

    @Delete("DELETE FROM mf_meeting_member WHERE meeting_id = #{id} AND user_id = #{userId} AND member_role = #{expected}")
    int removeMember(@Param("id") String id, @Param("userId") long userId, @Param("expected") String expected);

    @Insert("INSERT INTO mf_meeting_member_audit (meeting_id, actor_id, target_id, action, old_role, new_role) VALUES (#{id}, #{actor}, #{target}, #{action}, #{oldRole}, #{newRole})")
    int auditMember(@Param("id") String id, @Param("actor") long actor, @Param("target") long target,
                    @Param("action") String action, @Param("oldRole") String oldRole, @Param("newRole") String newRole);

    @Select("""
            SELECT u.username, 'ORGANIZER' AS memberRole, u.enabled
            FROM mf_meeting m JOIN mf_user u ON u.id = m.owner_id
            WHERE m.id = #{id}
            UNION ALL
            SELECT u.username, mm.member_role AS memberRole, u.enabled
            FROM mf_meeting_member mm JOIN mf_user u ON u.id = mm.user_id
            WHERE mm.meeting_id = #{id}
            ORDER BY memberRole, username
            """)
    List<Member> members(@Param("id") String id);

    @Select("""
            SELECT u.username FROM mf_user u
            WHERE u.enabled = TRUE AND u.id != #{actor}
              AND u.username LIKE #{prefix} ESCAPE '!'
              AND NOT EXISTS (SELECT 1 FROM mf_meeting_member mm WHERE mm.meeting_id = #{id} AND mm.user_id = u.id)
            ORDER BY u.username LIMIT 20
            """)
    List<String> findEligibleUsers(@Param("id") String id, @Param("actor") long actor, @Param("prefix") String prefix);

    @Select("SELECT COALESCE(MAX(version), 0) FROM mf_transcript_version WHERE meeting_id = #{id}")
    int latestVersion(@Param("id") String id);

    @Insert("INSERT INTO mf_transcript_version (meeting_id, version, content, created_by) VALUES (#{id}, #{version}, #{content}, #{userId})")
    int insertTranscript(@Param("id") String id, @Param("version") int version, @Param("content") String content, @Param("userId") long userId);

    /** 将新建文字稿版本与来源 AI 草稿关联，必须在同一事务内执行。 */
    @Update("UPDATE mf_transcript_version SET ai_draft_id=#{draftId} WHERE meeting_id=#{id} AND version=#{version}")
    int attachTranscriptDraft(@Param("id") String id,@Param("version") int version,@Param("draftId") String draftId);

    /** 查询文字稿版本关联的 AI 草稿 ID；手工版本为空。 */
    @Select("SELECT ai_draft_id FROM mf_transcript_version WHERE meeting_id=#{id} AND version=#{version}")
    String transcriptDraftId(@Param("id") String id,@Param("version") int version);

    @Select("SELECT version, content, created_at AS createdAt FROM mf_transcript_version WHERE meeting_id = #{id} ORDER BY version DESC LIMIT 1")
    Transcript latestTranscript(@Param("id") String id);

    @Select("SELECT version, content, created_at AS createdAt FROM mf_transcript_version WHERE meeting_id = #{id} AND version = #{version}")
    Transcript transcript(@Param("id") String id, @Param("version") int version);

    /** 会议查询结果。
     * @param id 会议唯一标识
     * @param title 会议主题
     * @param meetingTime 会议时间，可为空
     * @param createdAt 创建时间
     */
    record Meeting(String id, String title, LocalDateTime meetingTime, LocalDateTime createdAt) { }
    /** 会议成员查询结果。
     * @param username 成员用户名
     * @param memberRole 会议角色：组织者、编辑者或查看者
     * @param enabled 用户账号是否启用
     */
    record Member(String username, String memberRole, boolean enabled) { }
    /** 一版会议文字稿。
     * @param version 文字稿版本号，从 1 开始
     * @param content 文字稿内容
     * @param createdAt 版本创建时间
     */
    record Transcript(int version, String content, LocalDateTime createdAt) { }
}
