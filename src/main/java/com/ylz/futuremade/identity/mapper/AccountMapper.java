package com.ylz.futuremade.identity.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface AccountMapper {
    @Select("SELECT id, username, password_hash, enabled FROM mf_user WHERE username = #{username}")
    Account findByUsername(@Param("username") String username);

    @Select("""
            SELECT r.code FROM mf_role r
            JOIN mf_user_role ur ON ur.role_id = r.id
            WHERE ur.user_id = #{userId} AND r.enabled = TRUE
            """)
    List<String> findRoleCodes(@Param("userId") long userId);

    /** 认证所需的账号数据；密码哈希不得返回给前端。
     * @param id 用户唯一标识
     * @param username 登录用户名
     * @param passwordHash BCrypt 密码哈希
     * @param enabled 账号是否启用
     */
    record Account(long id, String username, String passwordHash, boolean enabled) { }
}
