package com.bsp.admin.module.role;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.role.domain.Permission;
import com.bsp.admin.module.role.domain.Role;
import com.bsp.admin.module.role.domain.RolePermission;
import com.bsp.admin.module.role.dto.PermissionTreeNode;
import com.bsp.admin.module.role.dto.RoleAssignPermissionRequest;
import com.bsp.admin.module.role.dto.RoleListItem;
import com.bsp.admin.module.role.dto.RoleOption;
import com.bsp.admin.module.role.dto.RolePermissionResponse;
import com.bsp.admin.module.role.dto.RoleSaveRequest;
import com.bsp.admin.module.user.domain.UserRole;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 角色与权限服务
 */
@Service
@RequiredArgsConstructor
public class RoleService {

    /** 内置角色类型 */
    private static final int TYPE_BUILTIN = 1;

    private final Repositories repositories;
    private final PermissionService permissionService;
    /** 操作日志（2026-09-11 用户需求：所有数据变动均需留痕） */
    private final LogService logService;

    /** 分页查询：名称/编码模糊 + 状态精确 */
    public PageResult<RoleListItem> page(int current, int size, String name, String code,
                                         Integer status, Map<String, RowFilters.Condition> filters) {
        requirePerm("system:role:list");
        Map<Long, Long> userCountByRole = repositories.getUserRole().findAll().stream()
                .collect(Collectors.groupingBy(UserRole::getRoleId, Collectors.counting()));

        List<RoleListItem> items = repositories.getRole().findAll().stream()
                .filter(r -> name == null || name.isBlank()
                        || (r.getName() != null && r.getName().contains(name.trim())))
                .filter(r -> code == null || code.isBlank()
                        || (r.getCode() != null && r.getCode().contains(code.trim())))
                .filter(r -> status == null || status.equals(r.getStatus()))
                .sorted(Comparator.comparing(Role::getId))
                .map(r -> new RoleListItem(
                        r.getId(), r.getName(), r.getCode(), r.getType(), r.getDataScope(),
                        r.getCustomOrgIds(), r.getRemark(), r.getStatus(),
                        userCountByRole.getOrDefault(r.getId(), 0L),
                        r.getCreateTime(), r.getUpdateTime()))
                .toList();
        List<RoleListItem> filtered = items.stream()
                .filter(item -> RowFilters.match(item, filters, ROLE_FILTER_FIELDS))
                .toList();
        return PageResult.of(filtered, current, size);
    }

    /** 通用列筛选白名单（表头漏斗字段，2026-09-10） */
    private static final Map<String, java.util.function.Function<RoleListItem, Object>> ROLE_FILTER_FIELDS = Map.ofEntries(
            Map.entry("name", RoleListItem::name),
            Map.entry("code", RoleListItem::code),
            Map.entry("type", RoleListItem::type),
            Map.entry("dataScope", RoleListItem::dataScope),
            Map.entry("userCount", RoleListItem::userCount),
            Map.entry("status", RoleListItem::status),
            Map.entry("updateTime", RoleListItem::updateTime));

    /** 启用角色下拉选项（用户表单分配角色） */
    public List<RoleOption> list() {
        return repositories.getRole().findAll().stream()
                .filter(r -> r.getStatus() != null && r.getStatus() == 1)
                .sorted(Comparator.comparing(Role::getId))
                .map(r -> new RoleOption(r.getId(), r.getName(), r.getCode()))
                .toList();
    }

    /** 新增（id 为空）或修改（id 非空；内置角色仅允许改名称/备注/状态） */
    public void save(RoleSaveRequest request) {
        requirePerm(request.id() == null ? "system:role:create" : "system:role:update");
        if (request.id() == null) {
            create(request);
        } else {
            update(request);
        }
    }

    private void create(RoleSaveRequest request) {
        validateCodeUnique(request.code(), null);
        Role role = new Role();
        role.setName(request.name());
        role.setCode(request.code());
        role.setType(2);
        role.setDataScope(request.dataScope() == null ? 4 : request.dataScope());
        role.setCustomOrgIds(request.customOrgIds());
        role.setRemark(request.remark());
        role.setStatus(request.status() == null ? 1 : request.status());
        role.setCreateTime(LocalDateTime.now());
        role.setUpdateTime(LocalDateTime.now());
        repositories.getRole().insert(role);
        logRole("新增角色", role, "编码 " + role.getCode() + " / 数据范围 " + role.getDataScope());
    }

    private void update(RoleSaveRequest request) {
        Role role = requireRole(request.id());
        if (role.getType() != null && role.getType() == TYPE_BUILTIN) {
            if (!role.getCode().equals(request.code())) {
                throw new BizException("内置角色的编码不允许修改");
            }
        } else {
            validateCodeUnique(request.code(), role.getId());
            role.setCode(request.code());
            role.setType(2);
        }
        role.setName(request.name());
        role.setRemark(request.remark());
        role.setStatus(request.status() == null ? role.getStatus() : request.status());
        role.setUpdateTime(LocalDateTime.now());
        repositories.getRole().updateById(role);
        logRole("编辑角色", role, "编码 " + role.getCode() + " / 数据范围 " + role.getDataScope()
                + " / 状态" + (Integer.valueOf(1).equals(role.getStatus()) ? "启用" : "停用"));
    }

    /** 删除角色：内置角色不可删；级联清理角色-权限与用户-角色关联 */
    public void delete(Long id) {
        requirePerm("system:role:delete");
        Role role = requireRole(id);
        if (role.getType() != null && role.getType() == TYPE_BUILTIN) {
            throw new BizException("内置角色不允许删除");
        }
        long permCount = repositories.getRolePermission().findAll().stream()
                .filter(rp -> id.equals(rp.getRoleId())).count();
        long userCount = repositories.getUserRole().findAll().stream()
                .filter(ur -> id.equals(ur.getRoleId())).count();
        repositories.getRolePermission().deleteIf(rp -> id.equals(rp.getRoleId()));
        repositories.getUserRole().deleteIf(ur -> id.equals(ur.getRoleId()));
        repositories.getRole().deleteById(id);
        logRole("删除角色", role, "同时解除权限 " + permCount + " 项、用户绑定 " + userCount + " 个");
    }

    /** 权限树最大递归深度（防御脏数据成环导致栈溢出） */
    private static final int MAX_TREE_DEPTH = 16;

    /**
     * 权限树（menu / api / data 三类，按 parent 组树，前端按 type 分组渲染）。
     *
     * <p><b>2026-09-11 修复</b>：原实现为「先给全部节点建快照，再逐个挂子节点」，第二遍取到的是
     * **第一遍的旧对象**，父节点永远只看到「还没有下级」的子节点——**三级及更深的权限会静默丢失**。
     * 与 {@code OrgService.tree()} 属同类缺陷，一并改为自顶向下递归装配。</p>
     */
    public List<PermissionTreeNode> permissionTree() {
        requirePerm("system:role:assign");
        List<Permission> all = repositories.getPermission().findAll().stream()
                .sorted(Comparator.comparing(Permission::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Permission::getId))
                .toList();
        Map<Long, List<Permission>> childrenByParent = all.stream()
                .collect(Collectors.groupingBy(p -> p.getParentId() == null ? 0L : p.getParentId()));
        return buildNodes(childrenByParent, 0L, 0);
    }

    private List<PermissionTreeNode> buildNodes(Map<Long, List<Permission>> childrenByParent,
                                                Long parentId, int depth) {
        if (depth > MAX_TREE_DEPTH) {
            return List.of();
        }
        return childrenByParent.getOrDefault(parentId, List.of()).stream()
                .map(p -> new PermissionTreeNode(p.getId(), p.getCode(), p.getName(), p.getType(),
                        p.getParentId(), p.getSort(),
                        buildNodes(childrenByParent, p.getId(), depth + 1)))
                .toList();
    }

    /** 角色已分配权限（回显） */
    public RolePermissionResponse rolePermissions(Long roleId) {
        Role role = requireRole(roleId);
        List<Long> permissionIds = repositories.getRolePermission().findAll().stream()
                .filter(rp -> roleId.equals(rp.getRoleId()))
                .map(RolePermission::getPermissionId)
                .toList();
        return new RolePermissionResponse(roleId, permissionIds, role.getDataScope(), role.getCustomOrgIds());
    }

    /** 角色授权：替换权限关联 + 保存数据范围设置 */
    public void assignPermissions(RoleAssignPermissionRequest request) {
        requirePerm("system:role:assign");
        Role role = requireRole(request.roleId());
        List<Long> permissionIds = request.permissionIds() == null ? List.of() : request.permissionIds();
        for (Long permissionId : permissionIds) {
            if (repositories.getPermission().findById(permissionId) == null) {
                throw new BizException("权限不存在: id=" + permissionId);
            }
        }

        repositories.getRolePermission().deleteIf(rp -> request.roleId().equals(rp.getRoleId()));
        for (Long permissionId : permissionIds) {
            RolePermission rolePermission = new RolePermission();
            rolePermission.setRoleId(request.roleId());
            rolePermission.setPermissionId(permissionId);
            rolePermission.setCreateTime(LocalDateTime.now());
            repositories.getRolePermission().insert(rolePermission);
        }

        Integer dataScope = request.dataScope() == null ? role.getDataScope() : request.dataScope();
        validateDataScope(dataScope, request.customOrgIds());
        role.setDataScope(dataScope);
        role.setCustomOrgIds(request.dataScope() != null && request.dataScope() == 6
                ? request.customOrgIds() : null);
        role.setUpdateTime(LocalDateTime.now());
        repositories.getRole().updateById(role);
        logRole("角色授权", role, "权限 " + permissionIds.size() + " 项 / 数据范围 " + dataScope);
    }

    /** 数据范围 = 6 时自定义部门集合必填且须存在 */
    private void validateDataScope(Integer dataScope, List<Long> customOrgIds) {
        if (dataScope == null || dataScope < 1 || dataScope > 6) {
            throw new BizException("数据范围取值非法");
        }
        if (dataScope == 6) {
            if (customOrgIds == null || customOrgIds.isEmpty()) {
                throw new BizException("自定义部门范围须至少选择一个部门");
            }
            for (Long orgId : customOrgIds) {
                if (repositories.getOrg().findById(orgId) == null) {
                    throw new BizException("部门不存在: id=" + orgId);
                }
            }
        }
    }

    private void validateCodeUnique(String code, Long excludeId) {
        boolean duplicated = repositories.getRole().findAll().stream()
                .anyMatch(r -> code.equals(r.getCode()) && !r.getId().equals(excludeId));
        if (duplicated) {
            throw new BizException("角色编码已存在: " + code);
        }
    }

    private Role requireRole(Long id) {
        Role role = repositories.getRole().findById(id);
        if (role == null) {
            throw new BizException("角色不存在: id=" + id);
        }
        return role;
    }

    /** 判权：系统管理类接口按权限点校验（2026-09-10 权限审计修复） */
    private void requirePerm(String code) {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), code)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
    }


    /** 角色日志：对象格式 名称(id)，与其他模块一致 */
    private void logRole(String action, Role role, String detail) {
        logService.addLogCurrentUser("role", role.getName() + "(" + role.getId() + ")", action, detail);
    }
}