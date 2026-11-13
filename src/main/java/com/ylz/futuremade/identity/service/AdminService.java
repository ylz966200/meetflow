package com.ylz.futuremade.identity.service;

import com.ylz.futuremade.identity.mapper.AccountMapper;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class AdminService {
    private static final Logger log = LoggerFactory.getLogger(AdminService.class);
    private final AdminMapper mapper;
    private final AccountMapper accounts;
    private final PasswordEncoder encoder;

    public AdminService(AdminMapper mapper, AccountMapper accounts, PasswordEncoder encoder) {
        this.mapper = mapper;
        this.accounts = accounts;
        this.encoder = encoder;
    }

    public List<AdminMapper.UserRow> users(int page, int size) {
        if (page < 1 || page > 10000 || size < 1 || size > 100) throw bad("分页参数无效");
        return mapper.users(size, (page - 1) * size);
    }

    public List<AdminMapper.RoleRow> roles() {
        return mapper.roles();
    }

    public List<Long> roleIds(long id) {
        requireUser(id);
        return mapper.roleIds(id);
    }

    @Transactional
    public void createUser(String name, String password, String actor) {
        if (name == null || !name.matches("[A-Za-z0-9_.-]{1,100}")) throw bad("用户名格式无效");
        if (password == null || password.length() < 12 || password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw bad("密码至少12个字符且不超过72字节");
        try {
            mapper.createUser(name, encoder.encode(password));
        } catch (DuplicateKeyException e) {
            throw conflict("用户名已存在");
        }
        log.info("管理员创建用户成功，操作者={}，用户名={}", actor, name);
    }

    @Transactional
    public void createRole(String code, String name, String actor) {
        try {
            if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,49}"))
                throw bad("角色编码须以大写英文字母开头，只能包含大写字母、数字和下划线，最长50个字符");
            if (name == null || name.isBlank() || name.length() > 100)
                throw bad("角色名称不能为空，且不能超过100个字符");
            mapper.createRole(code, name.strip());
        } catch (DuplicateKeyException e) {
            throw conflict("角色编码已存在");
        }
        log.info("管理员创建角色成功，操作者={}，角色={}", actor, code);
    }

    @Transactional
    public void updateRole(long id, String name, Boolean enabled, String actor) {
        lockAdmin();
        var role = mapper.role(id);
        if (role == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "角色不存在");
        if (name == null || name.isBlank() || name.length() > 100 || enabled == null) throw bad("角色名称或状态无效");
        if ("ADMIN".equals(role.code()) && !enabled) throw conflict("不能停用管理员角色");
        mapper.updateRole(id, name.strip(), enabled);
        log.info("管理员提交角色修改，操作者={}，角色ID={}", actor, id);
    }

    @Transactional
    public void setEnabled(long id, boolean enabled, String actor) {
        lockAdmin();
        var user = requireUser(id);
        ensureNotSelf(user.username(), actor);
        if (!enabled && user.enabled() && mapper.isAdmin(id) > 0 && mapper.countEnabledAdmins() <= 1)
            throw conflict("不能禁用最后一位启用的管理员");
        mapper.setEnabled(id, enabled);
        log.info("管理员修改用户状态，操作者={}，用户ID={}，启用={}", actor, id, enabled);
    }

    @Transactional
    public void setRoles(long id, List<Long> ids, String actor) {
        lockAdmin();
        var user = requireUser(id);
        ensureNotSelf(user.username(), actor);
        if (ids == null || ids.size() > 50 || ids.stream().anyMatch(Objects::isNull) || new HashSet<>(ids).size() != ids.size())
            throw bad("角色列表无效");
        boolean keepAdmin = false;
        for (Long roleId : ids) {
            var r = mapper.role(roleId);
            if (r == null || !r.enabled()) throw bad("角色不存在或已停用");
            if ("ADMIN".equals(r.code())) keepAdmin = true;
        }
        if (user.enabled() && mapper.isAdmin(id) > 0 && !keepAdmin && mapper.countEnabledAdmins() <= 1)
            throw conflict("不能移除最后一位启用的管理员");
        mapper.clearRoles(id);
        for (Long roleId : ids) mapper.assignRole(id, roleId);
        log.info("管理员修改用户角色，操作者={}，用户ID={}", actor, id);
    }

    private void lockAdmin() {
        if (mapper.lockAdminRole() == null) throw conflict("未配置 ADMIN 角色");
    }

    private AdminMapper.UserRow requireUser(long id) {
        var u = mapper.user(id);
        if (u == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        return u;
    }

    private void ensureNotSelf(String username, String actor) {
        if (username.equals(actor)) throw conflict("不能修改自己的账号状态或角色");
    }

    private ResponseStatusException bad(String msg) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
    }

    private ResponseStatusException conflict(String msg) {
        return new ResponseStatusException(HttpStatus.CONFLICT, msg);
    }
}
