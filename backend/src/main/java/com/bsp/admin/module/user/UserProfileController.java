package com.bsp.admin.module.user;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.module.user.dto.AvatarUpdateRequest;
import com.bsp.admin.module.user.dto.PasswordChangeRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 个人中心接口（2026-09-10 用户需求）：本人信息 / 修改密码 / 更换头像
 *
 * <p>仅操作当前登录用户自身，登录即可（不做权限点限制）。</p>
 */
@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;

    /** 本人基本信息（含部门与角色名称、头像） */
    @GetMapping("/profile")
    public BaseResponse<Map<String, Object>> profile() {
        return BaseResponse.ok(userProfileService.profile());
    }

    /** 修改密码（校验原密码 + 新密码强度） */
    @PutMapping("/password")
    public BaseResponse<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        userProfileService.changePassword(request.oldPassword(), request.newPassword());
        return BaseResponse.ok();
    }

    /** 更换头像（data URL；传空字符串恢复默认） */
    @PutMapping("/avatar")
    public BaseResponse<Void> updateAvatar(@RequestBody AvatarUpdateRequest request) {
        userProfileService.updateAvatar(request == null ? null : request.avatar());
        return BaseResponse.ok();
    }
}
