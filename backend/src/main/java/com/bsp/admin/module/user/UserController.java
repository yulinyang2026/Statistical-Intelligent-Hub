package com.bsp.admin.module.user;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.user.dto.UserDetail;
import com.bsp.admin.module.user.dto.UserLeaveRequest;
import com.bsp.admin.module.user.dto.UserListItem;
import com.bsp.admin.module.user.dto.UserOption;
import com.bsp.admin.module.user.dto.UserResetPasswordRequest;
import com.bsp.admin.module.user.dto.UserSaveRequest;
import com.bsp.admin.module.user.dto.UserStatusRequest;
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
 * 用户与账号接口
 */
@RestController
@RequestMapping("/api/system/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /** 分页查询（姓名/工号/手机号模糊；状态/部门/角色精确） */
    @GetMapping("/page")
    public BaseResponse<PageResult<UserListItem>> page(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String employeeNo,
            @RequestParam(required = false) String mobile,
            @RequestParam(required = false) Integer status,
            @RequestParam(required = false) Long orgId,
            @RequestParam(required = false) Long roleId,
            @RequestParam(required = false) String filters) {
        return BaseResponse.ok(userService.page(current, size, name, employeeNo,
                mobile, status, orgId, roleId, com.bsp.admin.common.filter.RowFilters.parse(filters)));
    }

    /** 用户详情（表单回显） */
    @GetMapping("/{id}")
    public BaseResponse<UserDetail> detail(@PathVariable Long id) {
        return BaseResponse.ok(userService.detail(id));
    }

    /** 新增用户 */
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody UserSaveRequest request) {
        userService.save(request);
        return BaseResponse.ok();
    }

    /** 修改用户（密码留空不变更） */
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody UserSaveRequest request) {
        userService.save(request);
        return BaseResponse.ok();
    }

    /** 重置密码 */
    @PutMapping("/reset-password")
    public BaseResponse<Void> resetPassword(@Valid @RequestBody UserResetPasswordRequest request) {
        userService.resetPassword(request);
        return BaseResponse.ok();
    }

    /** 启用/禁用（用户与账号状态联动） */
    @PutMapping("/status")
    public BaseResponse<Void> changeStatus(@Valid @RequestBody UserStatusRequest request) {
        userService.changeStatus(request);
        return BaseResponse.ok();
    }

    /** 离职处理 */
    @PostMapping("/leave")
    public BaseResponse<Void> leave(@Valid @RequestBody UserLeaveRequest request) {
        userService.leave(request.userId());
        return BaseResponse.ok();
    }

    /** 删除用户（物理删除：连账号与部门/角色绑定一并移除，2026-09-11 用户需求） */
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return BaseResponse.ok();
    }

    /** 用户下拉选项（部门负责人 / 直属主管选择） */
    @GetMapping("/options")
    public BaseResponse<List<UserOption>> options(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long excludeUserId) {
        return BaseResponse.ok(userService.options(keyword, excludeUserId));
    }
}
