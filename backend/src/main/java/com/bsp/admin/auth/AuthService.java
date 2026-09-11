package com.bsp.admin.auth;

import com.bsp.admin.auth.dto.LoginRequest;
import com.bsp.admin.auth.dto.LoginResponse;
import com.bsp.admin.auth.dto.UserInfoResponse;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.role.domain.Permission;
import com.bsp.admin.module.role.domain.Role;
import com.bsp.admin.module.user.domain.Account;
import com.bsp.admin.module.user.domain.User;
import com.bsp.admin.module.user.domain.UserRole;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

/**
 * 登录与用户信息服务
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    /** 连续登录失败锁定阈值 */
    private static final int MAX_FAIL_COUNT = 5;

    /** 锁定时长（分钟） */
    private static final long LOCK_MINUTES = 10;

    /** 超级管理员角色编码（跳过权限集合计算，全量放行） */
    private static final String SUPER_ROLE_CODE = "R_SUPER";

    private final Repositories repositories;
    private final TokenService tokenService;
    private final PasswordEncoder passwordEncoder;
    /** 操作日志（2026-09-11 用户需求：登录、登录失败、退出均需留痕） */
    private final LogService logService;

    /**
     * 登录：账号校验 + 防爆破 + 发放 token。
     *
     * <p>2026-09-11 用户需求：登录成功、登录失败（账号不存在 / 密码错误 / 已锁定 / 已停用）全部写操作日志。
     * 登录发生在鉴权之前，无法取当前登录用户，故显式传入操作人（账号名或姓名）。</p>
     */
    public LoginResponse login(LoginRequest request) {
        String attempted = request.userName();
        Account account = repositories.getAccount().findAll().stream()
                .filter(a -> attempted.equals(a.getUsername()))
                .findFirst()
                .orElseThrow(() -> {
                    logService.addLog("auth", attempted, "登录失败", "账号不存在", attempted);
                    return new BizException("用户名或密码错误");
                });

        if (account.getLockUntil() != null && account.getLockUntil().isAfter(LocalDateTime.now())) {
            long minutes = java.time.Duration.between(LocalDateTime.now(), account.getLockUntil()).toMinutes() + 1;
            logService.addLog("auth", attempted + "(" + account.getUserId() + ")", "登录失败",
                    "账号已锁定，剩余 " + minutes + " 分钟", attempted);
            throw new BizException("账号已锁定，请 " + minutes + " 分钟后重试");
        }

        User user = repositories.getUser().findById(account.getUserId());

        if (user == null || user.getStatus() == null || user.getStatus() != 1
                || account.getStatus() == null || account.getStatus() != 1) {
            logService.addLog("auth", attempted + "(" + account.getUserId() + ")", "登录失败",
                    "账号已停用", attempted);
            throw new BizException("账号已停用，请联系管理员");
        }

        if (!passwordEncoder.matches(request.password(), account.getCredential())) {
            recordLoginFailure(account);
            logService.addLog("auth", attempted + "(" + account.getUserId() + ")", "登录失败",
                    "密码错误", user.getName());
            throw new BizException("用户名或密码错误");
        }

        // 登录成功：清零失败计数
        if (account.getFailCount() != null && account.getFailCount() > 0) {
            account.setFailCount(0);
            account.setLockUntil(null);
        }
        account.setUpdateTime(LocalDateTime.now());
        repositories.getAccount().updateById(account);
        logService.addLog("auth", user.getName() + "(" + user.getId() + ")", "登录", "登录成功", user.getName());

        return new LoginResponse(tokenService.issue(user.getId()), tokenService.issue(user.getId()));
    }

    /** 登录失败计数：达到阈值锁定一段时间 */
    private void recordLoginFailure(Account account) {
        int failCount = (account.getFailCount() == null ? 0 : account.getFailCount()) + 1;
        account.setFailCount(failCount);
        if (failCount >= MAX_FAIL_COUNT) {
            account.setLockUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
            account.setFailCount(0);
        }
        account.setUpdateTime(LocalDateTime.now());
        repositories.getAccount().updateById(account);
    }

    /** 用户信息：按钮权限 = 所挂全部启用角色的 api 权限并集（超级管理员直接全量） */
    public UserInfoResponse getUserInfo(Long userId) {
        User user = repositories.getUser().findById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        Account account = repositories.getAccount().findAll().stream()
                .filter(a -> userId.equals(a.getUserId()))
                .findFirst()
                .orElse(null);

        List<Role> roles = repositories.getUserRole().findAll().stream()
                .filter(ur -> userId.equals(ur.getUserId()))
                .map(ur -> repositories.getRole().findById(ur.getRoleId()))
                .filter(r -> r != null && r.getStatus() != null && r.getStatus() == 1)
                .sorted(Comparator.comparing(Role::getId))
                .toList();

        List<String> roleCodes = roles.stream().map(Role::getCode).toList();

        List<String> buttons;
        List<String> menus;
        if (roleCodes.contains(SUPER_ROLE_CODE)) {
            buttons = repositories.getPermission().findAll().stream()
                    .filter(p -> "api".equals(p.getType()))
                    .map(Permission::getCode)
                    .toList();
            menus = repositories.getPermission().findAll().stream()
                    .filter(p -> "menu".equals(p.getType()))
                    .map(Permission::getCode)
                    .toList();
        } else {
            List<Long> roleIds = roles.stream().map(Role::getId).toList();
            List<Long> permissionIds = repositories.getRolePermission().findAll().stream()
                    .filter(rp -> roleIds.contains(rp.getRoleId()))
                    .map(rp -> rp.getPermissionId())
                    .distinct()
                    .toList();
            buttons = repositories.getPermission().findAll().stream()
                    .filter(p -> permissionIds.contains(p.getId()) && "api".equals(p.getType()))
                    .map(Permission::getCode)
                    .toList();
            menus = repositories.getPermission().findAll().stream()
                    .filter(p -> permissionIds.contains(p.getId()) && "menu".equals(p.getType()))
                    .map(Permission::getCode)
                    .toList();
        }

        return new UserInfoResponse(
                buttons,
                menus,
                roleCodes,
                userId,
                account == null ? "" : account.getUsername(),
                user.getEmail() == null ? "" : user.getEmail(),
                user.getAvatar() == null ? "" : user.getAvatar()
        );
    }

    /** 登出：吊销 token（前端纯前端清理，不调用本接口，保留作后续扩展） */
    /** 退出登录：吊销 token + 写操作日志（2026-09-11 用户需求：退出需留痕） */
    public void logout(String token) {
        Long userId = AuthContext.getUserId();
        if (userId != null) {
            User user = repositories.getUser().findById(userId);
            logService.addLogCurrentUser("auth",
                    user == null ? String.valueOf(userId) : user.getName() + "(" + userId + ")", "退出登录", "—");
        }
        tokenService.revoke(token);
    }
}
