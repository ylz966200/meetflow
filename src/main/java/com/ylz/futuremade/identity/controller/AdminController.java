package com.ylz.futuremade.identity.controller;


import com.ylz.futuremade.identity.model.dto.CreateUserRequest;
import com.ylz.futuremade.identity.model.dto.StatusRequest;
import com.ylz.futuremade.identity.model.dto.RoleAssignmentRequest;
import com.ylz.futuremade.identity.model.dto.CreateRoleRequest;
import com.ylz.futuremade.identity.model.dto.UpdateRoleRequest;
import com.ylz.futuremade.identity.mapper.AdminMapper;
import com.ylz.futuremade.identity.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.security.Principal;
import java.util.List;
import com.ylz.futuremade.common.model.dto.ApiError;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

/**
 * 系统管理控制器：提供管理员对用户账号、系统角色及用户角色分配的管理接口。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService service;

    public AdminController(AdminService service) {
        this.service = service;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> handleBusinessError(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(new ApiError(ex.getReason() == null ? "操作失败" : ex.getReason()));
    }

    /** 分页查询系统用户。 */
    @GetMapping("/users")
    public List<AdminMapper.UserRow> users(@RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return service.users(page, size);
    }

    /** 创建用户账号并保存密码哈希。 */
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public void createUser(@RequestBody CreateUserRequest input, Principal principal) {
        service.createUser(input.getUsername(), input.getPassword(), principal.getName());
    }

    /** 查询指定用户已分配的系统角色。 */
    @GetMapping("/users/{id}/roles")
    public List<Long> userRoles(@PathVariable long id) {
        return service.roleIds(id);
    }

    /** 为指定用户设置系统角色。 */
    @PutMapping("/users/{id}/roles")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void roles(@PathVariable long id, @RequestBody RoleAssignmentRequest input, Principal principal) {
        service.setRoles(id, input.getRoleIds(), principal.getName());
    }

    /** 启用或禁用指定用户账号。 */
    @PutMapping("/users/{id}/status")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void status(@PathVariable long id, @RequestBody StatusRequest input, Principal principal) {
        if (input.getEnabled() == null)
            throw new org.springframework.web.server.ResponseStatusException(HttpStatus.BAD_REQUEST, "状态不能为空");
        service.setEnabled(id, input.getEnabled(), principal.getName());
    }

    /** 查询系统角色列表。 */
    @GetMapping("/roles")
    public List<AdminMapper.RoleRow> roles() {
        return service.roles();
    }

    /** 创建系统角色。 */
    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public void createRole(@RequestBody CreateRoleRequest input, Principal principal) {
        service.createRole(input.getCode(), input.getName(), principal.getName());
    }

    /** 修改系统角色名称或启用状态。 */
    @PutMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateRole(@PathVariable long id, @RequestBody UpdateRoleRequest input, Principal principal) {
        service.updateRole(id, input.getName(), input.getEnabled(), principal.getName());
    }
}
