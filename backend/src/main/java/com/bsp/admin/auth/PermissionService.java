package com.bsp.admin.auth;

import com.bsp.admin.module.role.domain.Permission;
import com.bsp.admin.module.role.domain.Role;
import com.bsp.admin.module.user.domain.User;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * 权限点判定服务：业务层按权限点（非角色名）做服务端校验的统一入口。
 * 权限码集合计算与 {@link AuthService#getUserInfo} 的 buttons 同构（超级管理员全量放行）。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    /** 超级管理员角色编码（跳过权限集合计算，全量放行） */
    private static final String SUPER_ROLE_CODE = "R_SUPER";

    private final Repositories repositories;

    /** 某用户全部启用角色的 api 型权限码并集 */
    public List<String> apiCodes(Long userId) {
        if (userId == null) {
            return List.of();
        }
        List<Role> roles = repositories.getUserRole().findAll().stream()
                .filter(ur -> userId.equals(ur.getUserId()))
                .map(ur -> repositories.getRole().findById(ur.getRoleId()))
                .filter(r -> r != null && r.getStatus() != null && r.getStatus() == 1)
                .sorted(Comparator.comparing(Role::getId))
                .toList();
        if (roles.stream().map(Role::getCode).anyMatch(SUPER_ROLE_CODE::equals)) {
            return repositories.getPermission().findAll().stream()
                    .filter(p -> "api".equals(p.getType()))
                    .map(Permission::getCode)
                    .toList();
        }
        List<Long> roleIds = roles.stream().map(Role::getId).toList();
        List<Long> permissionIds = repositories.getRolePermission().findAll().stream()
                .filter(rp -> roleIds.contains(rp.getRoleId()))
                .map(rp -> rp.getPermissionId())
                .distinct()
                .toList();
        return repositories.getPermission().findAll().stream()
                .filter(p -> permissionIds.contains(p.getId()) && "api".equals(p.getType()))
                .map(Permission::getCode)
                .toList();
    }

    /** 是否持有某权限点 */
    public boolean hasApiPerm(Long userId, String code) {
        return userId != null && apiCodes(userId).contains(code);
    }

    /** 当前登录用户的中文姓名（任务 assignee 存姓名，数据范围按姓名匹配；查无则 null） */
    public String currentUserName() {
        Long userId = AuthContext.getUserId();
        if (userId == null) {
            return null;
        }
        User user = repositories.getUser().findById(userId);
        return user == null ? null : user.getName();
    }
}
