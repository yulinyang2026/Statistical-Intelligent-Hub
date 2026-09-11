package com.bsp.admin.module.user;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.org.domain.Org;
import com.bsp.admin.module.user.domain.Account;
import com.bsp.admin.module.user.domain.User;
import com.bsp.admin.module.user.domain.UserOrg;
import com.bsp.admin.module.user.domain.UserRole;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 个人中心服务（2026-09-10 用户需求）：本人基本信息、修改密码、更换头像
 *
 * <p>仅作用于当前登录用户，不做权限点限制（登录即可操作自己的资料）。</p>
 */
@Service
@RequiredArgsConstructor
public class UserProfileService {

    /** 头像 data URL 允许的图片类型 */
    private static final Pattern AVATAR_PATTERN =
            Pattern.compile("^data:image/(png|jpe?g|webp|gif);base64,[A-Za-z0-9+/=]+$");

    /** 头像 data URL 最大长度（约 300KB 原图，前端已压缩到 128×128） */
    private static final int AVATAR_MAX_LENGTH = 400_000;

    /** 新密码最小长度 */
    private static final int PASSWORD_MIN_LENGTH = 8;

    private final Repositories repositories;
    private final PasswordEncoder passwordEncoder;
    private final LogService logService;

    /** 本人信息（含部门与角色名称，供个人中心展示） */
    public Map<String, Object> profile() {
        Long userId = requireUserId();
        User user = requireUser(userId);
        Account account = findAccount(userId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("userId", user.getId());
        result.put("name", user.getName());
        result.put("employeeNo", user.getEmployeeNo());
        result.put("mobile", user.getMobile());
        result.put("email", user.getEmail());
        result.put("avatar", user.getAvatar() == null ? "" : user.getAvatar());
        result.put("status", user.getStatus());
        result.put("username", account == null ? "" : account.getUsername());
        result.put("orgNames", orgNames(userId));
        result.put("roleNames", roleNames(userId));
        result.put("createTime", user.getCreateTime());
        return result;
    }

    /** 修改密码：校验原密码 → 校验新密码强度 → BCrypt 重新哈希 */
    public void changePassword(String oldPassword, String newPassword) {
        Long userId = requireUserId();
        Account account = findAccount(userId);
        if (account == null) {
            throw new BizException("当前账号不存在登录凭证");
        }
        if (!passwordEncoder.matches(oldPassword, account.getCredential())) {
            throw new BizException("原密码不正确");
        }
        if (newPassword.length() < PASSWORD_MIN_LENGTH) {
            throw new BizException("新密码至少 " + PASSWORD_MIN_LENGTH + " 位");
        }
        if (!newPassword.matches(".*[A-Za-z].*") || !newPassword.matches(".*\\d.*")) {
            throw new BizException("新密码需同时包含字母与数字");
        }
        if (passwordEncoder.matches(newPassword, account.getCredential())) {
            throw new BizException("新密码不能与原密码相同");
        }
        account.setCredential(passwordEncoder.encode(newPassword));
        account.setUpdateTime(LocalDateTime.now());
        repositories.getAccount().updateById(account);
        logService.addLogCurrentUser("user", account.getUsername() + "(" + userId + ")", "修改密码", "本人自助修改");
    }

    /** 更换头像（data URL；空字符串 = 恢复系统默认头像） */
    public void updateAvatar(String avatar) {
        Long userId = requireUserId();
        User user = requireUser(userId);
        String value = avatar == null ? "" : avatar.trim();
        if (!value.isEmpty()) {
            if (value.length() > AVATAR_MAX_LENGTH) {
                throw new BizException("头像文件过大，请重新选择（建议 300KB 以内）");
            }
            if (!AVATAR_PATTERN.matcher(value).matches()) {
                throw new BizException("头像格式不支持，请上传 png/jpg/webp/gif 图片");
            }
        }
        user.setAvatar(value.isEmpty() ? null : value);
        user.setUpdateTime(LocalDateTime.now());
        repositories.getUser().updateById(user);
        logService.addLogCurrentUser("user", user.getName() + "(" + userId + ")", "更换头像",
                value.isEmpty() ? "恢复默认头像" : "更新头像");
    }

    // ==================== 内部工具 ====================

    private Long requireUserId() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            throw new BizException("请先登录");
        }
        return userId;
    }

    private User requireUser(Long userId) {
        User user = repositories.getUser().findById(userId);
        if (user == null) {
            throw new BizException("用户不存在");
        }
        return user;
    }

    private Account findAccount(Long userId) {
        return repositories.getAccount().findAll().stream()
                .filter(a -> userId.equals(a.getUserId()))
                .findFirst()
                .orElse(null);
    }

    /** 部门名称（主属在前） */
    private List<String> orgNames(Long userId) {
        Map<Long, String> orgNameById = repositories.getOrg().findAll().stream()
                .collect(java.util.stream.Collectors.toMap(Org::getId, Org::getName, (a, b) -> a));
        return repositories.getUserOrg().findAll().stream()
                .filter(uo -> userId.equals(uo.getUserId()))
                .sorted(Comparator.comparing((UserOrg uo) -> !Boolean.TRUE.equals(uo.getIsPrimary())))
                .map(UserOrg::getOrgId)
                .map(orgNameById::get)
                .filter(Objects::nonNull)
                .toList();
    }

    /** 启用角色名称 */
    private List<String> roleNames(Long userId) {
        return repositories.getUserRole().findAll().stream()
                .filter(ur -> userId.equals(ur.getUserId()))
                .map(UserRole::getRoleId)
                .distinct()
                .map(id -> repositories.getRole().findById(id))
                .filter(r -> r != null && r.getStatus() != null && r.getStatus() == 1)
                .sorted(Comparator.comparing(com.bsp.admin.module.role.domain.Role::getId))
                .map(com.bsp.admin.module.role.domain.Role::getName)
                .toList();
    }
}
