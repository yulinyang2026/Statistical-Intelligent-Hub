package com.bsp.admin.module.role;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.role.dto.PermissionTreeNode;
import com.bsp.admin.module.role.dto.RoleAssignPermissionRequest;
import com.bsp.admin.module.role.dto.RoleListItem;
import com.bsp.admin.module.role.dto.RoleOption;
import com.bsp.admin.module.role.dto.RolePermissionResponse;
import com.bsp.admin.module.role.dto.RoleSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 角色管理接口
 */
@RestController
@RequestMapping("/api/system/role")
@RequiredArgsConstructor
public class RoleController {

    private final RoleService roleService;

    /** 分页查询 */
    @GetMapping("/page")
    public BaseResponse<PageResult<RoleListItem>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) String filters) {
        return BaseResponse.ok(roleService.page(current, size, name, code, status, com.bsp.admin.common.filter.RowFilters.parse(filters)));
    }

    /** 启用角色下拉选项 */
    @GetMapping("/list")
    public BaseResponse<List<RoleOption>> list() {
        return BaseResponse.ok(roleService.list());
    }

    /** 新增角色 */
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody RoleSaveRequest request) {
        roleService.save(request);
        return BaseResponse.ok();
    }

    /** 修改角色 */
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody RoleSaveRequest request) {
        roleService.save(request);
        return BaseResponse.ok();
    }

    /** 删除角色（内置不可删） */
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        roleService.delete(id);
        return BaseResponse.ok();
    }

    /** 权限树（menu / api / data 三类） */
    @GetMapping("/permission-tree")
    public BaseResponse<List<PermissionTreeNode>> permissionTree() {
        return BaseResponse.ok(roleService.permissionTree());
    }

    /** 角色已分配权限（回显） */
    @GetMapping("/{id}/permissions")
    public BaseResponse<RolePermissionResponse> rolePermissions(@PathVariable Long id) {
        return BaseResponse.ok(roleService.rolePermissions(id));
    }

    /** 角色授权（权限勾选 + 数据范围） */
    @PutMapping("/permissions")
    public BaseResponse<Void> assignPermissions(@Valid @RequestBody RoleAssignPermissionRequest request) {
        roleService.assignPermissions(request);
        return BaseResponse.ok();
    }
}
