package com.ylz.futuremade.identity.mapper;

import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface AdminMapper {
    @Select("SELECT id, username, enabled FROM mf_user ORDER BY id LIMIT #{limit} OFFSET #{offset}")
    List<UserRow> users(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT id, code, name, enabled FROM mf_role ORDER BY id")
    List<RoleRow> roles();

    @Select("SELECT id, username, enabled FROM mf_user WHERE id = #{id}")
    UserRow user(long id);

    @Select("SELECT id, code, name, enabled FROM mf_role WHERE id = #{id}")
    RoleRow role(long id);

    @Select("SELECT id FROM mf_role WHERE code = 'ADMIN' FOR UPDATE")
    Long lockAdminRole();

    @Select("SELECT COUNT(*) FROM mf_user u JOIN mf_user_role ur ON u.id=ur.user_id JOIN mf_role r ON r.id=ur.role_id WHERE r.code='ADMIN' AND r.enabled=TRUE AND u.enabled=TRUE")
    int countEnabledAdmins();

    @Select("SELECT COUNT(*) FROM mf_user_role ur JOIN mf_role r ON r.id=ur.role_id WHERE ur.user_id=#{id} AND r.code='ADMIN' AND r.enabled=TRUE")
    int isAdmin(long id);

    @Insert("INSERT INTO mf_user (username, password_hash, enabled) VALUES (#{username}, #{hash}, TRUE)")
    int createUser(@Param("username") String username, @Param("hash") String hash);

    @Insert("INSERT INTO mf_role (code, name, enabled) VALUES (#{code}, #{name}, TRUE)")
    int createRole(@Param("code") String code, @Param("name") String name);

    @Update("UPDATE mf_user SET enabled=#{enabled} WHERE id=#{id}")
    int setEnabled(@Param("id") long id, @Param("enabled") boolean enabled);

    @Update("UPDATE mf_role SET name=#{name}, enabled=#{enabled} WHERE id=#{id}")
    int updateRole(@Param("id") long id, @Param("name") String name, @Param("enabled") boolean enabled);

    @Delete("DELETE FROM mf_user_role WHERE user_id=#{id}")
    int clearRoles(long id);

    @Insert("INSERT INTO mf_user_role(user_id,role_id) VALUES(#{userId},#{roleId})")
    int assignRole(@Param("userId") long userId, @Param("roleId") long roleId);

    @Select("SELECT role_id FROM mf_user_role WHERE user_id=#{id} ORDER BY role_id")
    List<Long> roleIds(long id);

    /** 用户管理列表行。
     * @param id 用户唯一标识
     * @param username 登录用户名
     * @param enabled 账号是否启用
     */
    record UserRow(long id, String username, boolean enabled) {
    }

    /** 系统角色查询结果。
     * @param id 角色唯一标识
     * @param code 系统角色编码
     * @param name 角色显示名称
     * @param enabled 角色是否启用
     */
    record RoleRow(long id, String code, String name, boolean enabled) {
    }
}
