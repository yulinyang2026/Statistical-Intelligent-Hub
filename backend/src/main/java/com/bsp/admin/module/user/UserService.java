package com.bsp.admin.module.user;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.org.domain.Org;
import com.bsp.admin.module.role.domain.Role;
import com.bsp.admin.module.user.domain.Account;
import com.bsp.admin.module.user.domain.User;
import com.bsp.admin.module.user.domain.UserOrg;
import com.bsp.admin.module.user.domain.UserRole;
import com.bsp.admin.module.user.dto.UserDetail;
import com.bsp.admin.module.user.dto.UserListItem;
import com.bsp.admin.module.user.dto.UserOption;
import com.bsp.admin.module.user.dto.UserOrgItem;
import com.bsp.admin.module.user.dto.UserResetPasswordRequest;
import com.bsp.admin.module.user.dto.UserSaveRequest;
import com.bsp.admin.module.user.dto.UserStatusRequest;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户与账号服务
 *
 * <p>业务主键统一 user_id；一人一账号；user_org 每个用户 is_primary 有且仅有 1 个为 true。</p>
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final Repositories repositories;
    private final PermissionService permissionService;
    private final PasswordEncoder passwordEncoder;
    /** 操作日志（2026-09-11 用户需求：所有数据变动均需留痕） */
    private final LogService logService;

    /** 分页查询：姓名/工号/手机号模糊 + 状态/部门/角色精确 */
    public PageResult<UserListItem> page(int current, int size, String name, String employeeNo,
                                         String mobile, Integer status, Long orgId, Long roleId,
                                         Map<String, RowFilters.Condition> filters) {
        requirePerm("system:user:list");
        // 部门过滤：orgId 命中主属或兼职均可
        Set<Long> userIdsByOrg = orgId == null ? null : repositories.getUserOrg().findAll().stream()
                .filter(uo -> orgId.equals(uo.getOrgId()))
                .map(UserOrg::getUserId)
                .collect(Collectors.toSet());
        // 角色过滤
        Set<Long> userIdsByRole = roleId == null ? null : repositories.getUserRole().findAll().stream()
                .filter(ur -> roleId.equals(ur.getRoleId()))
                .map(UserRole::getUserId)
                .collect(Collectors.toSet());

        List<User> users = repositories.getUser().findAll().stream()
                .filter(u -> name == null || name.isBlank()
                        || (u.getName() != null && u.getName().contains(name.trim())))
                .filter(u -> employeeNo == null || employeeNo.isBlank()
                        || (u.getEmployeeNo() != null && u.getEmployeeNo().contains(employeeNo.trim())))
                .filter(u -> mobile == null || mobile.isBlank()
                        || (u.getMobile() != null && u.getMobile().contains(mobile.trim())))
                .filter(u -> status == null || status.equals(u.getStatus()))
                .filter(u -> userIdsByOrg == null || userIdsByOrg.contains(u.getId()))
                .filter(u -> userIdsByRole == null || userIdsByRole.contains(u.getId()))
                .sorted(Comparator.comparing(User::getId).reversed())
                .toList();

        Map<Long, Account> accountByUserId = repositories.getAccount().findAll().stream()
                .collect(Collectors.toMap(Account::getUserId, a -> a, (a, b) -> a));
        Map<Long, String> userNameById = repositories.getUser().findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));
        Map<Long, String> roleNameById = repositories.getRole().findAll().stream()
                .collect(Collectors.toMap(Role::getId, Role::getName, (a, b) -> a));

        List<UserListItem> items = users.stream()
                .map(user -> toListItem(user, accountByUserId, userNameById, roleNameById))
                .toList();

        List<UserListItem> filtered = items.stream()
                .filter(item -> RowFilters.match(item, filters, USER_FILTER_FIELDS))
                .toList();
        return PageResult.of(filtered, current, size);
    }

    /** 用户详情（表单回显） */
    public UserDetail detail(Long id) {
        requirePerm("system:user:list");
        User user = requireUser(id);
        Account account = findAccount(user.getId());
        List<UserOrgItem> orgs = orgItems(user.getId());
        Long primaryOrgId = orgs.stream()
                .filter(UserOrgItem::isPrimary)
                .map(UserOrgItem::orgId)
                .findFirst().orElse(null);
        List<Long> roleIds = repositories.getUserRole().findAll().stream()
                .filter(ur -> user.getId().equals(ur.getUserId()))
                .map(UserRole::getRoleId)
                .toList();
        return new UserDetail(
                user.getId(),
                user.getName(),
                user.getEmployeeNo(),
                user.getMobile(),
                user.getEmail(),
                user.getManagerUserId(),
                user.getStatus(),
                account == null ? "" : account.getUsername(),
                orgs,
                orgs.stream().map(UserOrgItem::orgId).toList(),
                primaryOrgId,
                roleIds,
                user.getCreateTime()
        );
    }

    /** 新增（id 为空）或修改（id 非空） */
    public void save(UserSaveRequest request) {
        requirePerm(request.id() == null ? "system:user:create" : "system:user:update");
        if (request.id() == null) {
            create(request);
        } else {
            update(request);
        }
    }

    /**
     * 新增用户。
     *
     * <p><b>2026-09-11 修复</b>：原实现把「密码强度校验」「主管校验」「角色存在性校验」留在
     * {@code user.insert()} 之后，任一项不通过都会留下**没有登录账号的孤儿用户行**——此后每次重试都被
     * 工号/手机号/登录账号唯一性校验挡住，表现为「新增用户一直报用户名已存在 / 手机号已存在」。
     * 现将**全部校验前置到任何写入之前**，失败不留痕。</p>
     */
    private void create(UserSaveRequest request) {
        validateEmployeeNoUnique(request.employeeNo(), null);
        validateMobileUnique(request.mobile(), null);
        validateUsernameUnique(request.username(), null);
        validatePasswordStrength(request.password());
        validateOrgRelations(request.orgIds(), request.primaryOrgId());
        validateRolesExist(request.roleIds());
        validateManagerExists(request.managerUserId());

        User user = new User();
        user.setName(request.name());
        user.setEmployeeNo(request.employeeNo());
        user.setMobile(request.mobile());
        user.setEmail(request.email());
        user.setManagerUserId(request.managerUserId());
        user.setStatus(request.status() == null ? 1 : request.status());
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        repositories.getUser().insert(user);

        // 登录账号
        Account account = new Account();
        account.setUserId(user.getId());
        account.setUsername(request.username());
        account.setAuthType("password");
        account.setCredential(passwordEncoder.encode(request.password()));
        account.setStatus(user.getStatus());
        account.setFailCount(0);
        account.setCreateTime(LocalDateTime.now());
        account.setUpdateTime(LocalDateTime.now());
        repositories.getAccount().insert(account);

        saveRelations(user.getId(), request.orgIds(), request.primaryOrgId(), request.roleIds());
        applyOrgRole(request, user);
        logUser("新增用户", user, "工号 " + user.getEmployeeNo() + " / 手机号 " + user.getMobile()
                + " / 账号 " + request.username() + " / 角色 " + (request.roleIds() == null ? 0 : request.roleIds().size()) + " 个");
    }

    private void update(UserSaveRequest request) {
        User user = requireUser(request.id());
        // 校验全部前置（与新增同口径）：避免更新到一半失败留下不一致数据
        validateEmployeeNoUnique(request.employeeNo(), user.getId());
        validateMobileUnique(request.mobile(), user.getId());
        validateOrgRelations(request.orgIds(), request.primaryOrgId());
        validateRolesExist(request.roleIds());
        validateManagerExists(request.managerUserId());
        checkManagerCycle(user.getId(), request.managerUserId());
        Account account = findAccount(user.getId());
        if (account != null) {
            validateUsernameUnique(request.username(), user.getId());
        }
        boolean changePassword = request.password() != null && !request.password().isBlank();
        if (changePassword) {
            validatePasswordStrength(request.password());
        }

        user.setName(request.name());
        user.setEmployeeNo(request.employeeNo());
        user.setMobile(request.mobile());
        user.setEmail(request.email());
        user.setStatus(request.status() == null ? user.getStatus() : request.status());
        user.setUpdateTime(LocalDateTime.now());
        user.setManagerUserId(request.managerUserId());
        repositories.getUser().updateById(user);

        // 账号：用户名可改；密码留空不变更；状态与用户联动
        if (account != null) {
            account.setUsername(request.username());
            account.setStatus(user.getStatus());
            if (changePassword) {
                account.setCredential(passwordEncoder.encode(request.password()));
            }
            account.setUpdateTime(LocalDateTime.now());
            repositories.getAccount().updateById(account);
        }

        saveRelations(user.getId(), request.orgIds(), request.primaryOrgId(), request.roleIds());
        logUser("编辑用户", user, "工号 " + user.getEmployeeNo() + " / 手机号 " + user.getMobile()
                + (changePassword ? " / 同时重置了密码" : ""));
    }

    /** 角色必须存在（前置校验，避免写库后才失败） */
    private void validateRolesExist(List<Long> roleIds) {
        if (roleIds == null) {
            return;
        }
        for (Long roleId : roleIds) {
            if (roleId == null || repositories.getRole().findById(roleId) == null) {
                throw new BizException("角色不存在: id=" + roleId);
            }
        }
    }

    /** 直属主管必须存在（成环检查在编辑时另做） */
    private void validateManagerExists(Long managerUserId) {
        if (managerUserId == null || managerUserId == 0L) {
            return;
        }
        if (repositories.getUser().findById(managerUserId) == null) {
            throw new BizException("直属主管不存在: id=" + managerUserId);
        }
    }

    /** 重置密码 */
    public void resetPassword(UserResetPasswordRequest request) {
        requirePerm("system:user:reset-password");
        User user = requireUser(request.userId());
        Account account = findAccount(user.getId());
        if (account == null) {
            throw new BizException("用户没有登录账号");
        }
        // 2026-09-11 补齐：此前仅靠 DTO @Size(min=6) 兜底，FR-V5-027④ 的强度校验实际未落地
        validatePasswordStrength(request.password());
        account.setCredential(passwordEncoder.encode(request.password()));
        account.setUpdateTime(LocalDateTime.now());
        repositories.getAccount().updateById(account);
        logUser("重置密码", user, "账号 " + account.getUsername());
    }

    /**
     * 口令强度：≥8 位且同时含字母与数字。
     * 新建用户（初始密码）、重置密码、个人中心改密码三处口径一致，本方法为 Service 侧单一实现
     * （个人中心另有 {@code UserProfileService} 的等价校验，文案分属两处，如需改动请同步）。
     */
    private void validatePasswordStrength(String password) {
        if (password == null || password.isBlank()) {
            throw new BizException("密码不能为空");
        }
        if (password.length() < 8
                || !password.matches(".*[A-Za-z].*") || !password.matches(".*\\d.*")) {
            throw new BizException("密码至少 8 位，且需同时包含字母与数字");
        }
    }

    /** 启用/禁用：用户与账号状态联动 */
    public void changeStatus(UserStatusRequest request) {
        requirePerm("system:user:status");
        User user = requireUser(request.userId());
        user.setStatus(request.status());
        user.setUpdateTime(LocalDateTime.now());
        repositories.getUser().updateById(user);

        Account account = findAccount(user.getId());
        if (account != null) {
            account.setStatus(request.status());
            account.setUpdateTime(LocalDateTime.now());
            repositories.getAccount().updateById(account);
        }
        logUser(Integer.valueOf(1).equals(request.status()) ? "启用用户" : "停用用户", user,
                "账号 " + (account == null ? "—" : account.getUsername()));
    }

    /** 离职处理：禁用用户与账号、解除部门归属、清空部门负责人引用；保留角色与历史单据引用 */
    public void leave(Long userId) {
        requirePerm("system:user:leave");
        User user = requireUser(userId);
        user.setStatus(0);
        user.setUpdateTime(LocalDateTime.now());
        repositories.getUser().updateById(user);

        Account account = findAccount(userId);
        if (account != null) {
            account.setStatus(0);
            account.setUpdateTime(LocalDateTime.now());
            repositories.getAccount().updateById(account);
        }

        repositories.getUserOrg().deleteIf(uo -> userId.equals(uo.getUserId()));

        // 清空该用户担任的部门负责人
        for (Org org : repositories.getOrg().findAll()) {
            if (userId.equals(org.getLeaderUserId())) {
                org.setLeaderUserId(null);
                org.setUpdateTime(LocalDateTime.now());
                repositories.getOrg().updateById(org);
            }
        }
        logUser("离职处理", user, "已停用账号并解除部门归属");
    }

    /**
     * 删除用户（**物理删除**，2026-09-11 用户需求）。
     *
     * <p>与「离职」的区别：离职保留人员档案（仅停用），删除则把人员连同登录账号、部门归属、
     * 角色绑定一并移除，记录从列表消失。本工程任务/模块/项目等引用的是**姓名**而非用户 id，
     * 日志与历史记录存的也是「名称(id)」字符串，因此删除不会破坏既有数据。</p>
     *
     * <p>同时清理指向该用户的引用：其他人的「直属主管」、机构的「负责人」都会置空，避免悬空。</p>
     */
    public void delete(Long userId) {
        requirePerm("system:user:delete");
        User user = requireUser(userId);
        if (userId.equals(AuthContext.getUserId())) {
            throw new BizException("不能删除当前登录的账号");
        }

        repositories.getUserOrg().deleteIf(uo -> userId.equals(uo.getUserId()));
        repositories.getUserRole().deleteIf(ur -> userId.equals(ur.getUserId()));
        repositories.getAccount().deleteIf(a -> userId.equals(a.getUserId()));
        // 界面设置一并删除（否则主键复用后可能串到新用户）
        repositories.getUserSetting().deleteIf(s -> userId.equals(s.getUserId()));

        // 清空指向该用户的引用（下属的直属主管、机构的负责人）
        for (User other : repositories.getUser().findAll()) {
            if (userId.equals(other.getManagerUserId())) {
                other.setManagerUserId(null);
                other.setUpdateTime(LocalDateTime.now());
                repositories.getUser().updateById(other);
            }
        }
        for (Org org : repositories.getOrg().findAll()) {
            if (userId.equals(org.getLeaderUserId())) {
                org.setLeaderUserId(null);
                org.setUpdateTime(LocalDateTime.now());
                repositories.getOrg().updateById(org);
            }
        }

        repositories.getUser().deleteById(userId);
        logUser("删除用户", user, "账号与部门/角色绑定一并移除");
    }

    /** 用户下拉选项（部门负责人 / 直属主管） */
    public List<UserOption> options(String keyword, Long excludeUserId) {
        return repositories.getUser().findAll().stream()
                .filter(u -> u.getStatus() != null && u.getStatus() == 1)
                .filter(u -> excludeUserId == null || !excludeUserId.equals(u.getId()))
                .filter(u -> keyword == null || keyword.isBlank()
                        || (u.getName() != null && u.getName().contains(keyword.trim()))
                        || (u.getEmployeeNo() != null && u.getEmployeeNo().contains(keyword.trim())))
                .sorted(Comparator.comparing(User::getId))
                .map(u -> new UserOption(u.getId(), u.getName(), u.getEmployeeNo()))
                .toList();
    }

    /** 全量替换部门归属与角色关联 */
    private void saveRelations(Long userId, List<Long> orgIds, Long primaryOrgId, List<Long> roleIds) {
        repositories.getUserOrg().deleteIf(uo -> userId.equals(uo.getUserId()));
        if (orgIds != null) {
            validateOrgRelations(orgIds, primaryOrgId);
            for (Long orgId : orgIds) {
                UserOrg userOrg = new UserOrg();
                userOrg.setUserId(userId);
                userOrg.setOrgId(orgId);
                userOrg.setIsPrimary(orgId.equals(primaryOrgId));
                userOrg.setCreateTime(LocalDateTime.now());
                repositories.getUserOrg().insert(userOrg);
            }
        }

        repositories.getUserRole().deleteIf(ur -> userId.equals(ur.getUserId()));
        if (roleIds != null) {
            for (Long roleId : roleIds) {
                if (repositories.getRole().findById(roleId) == null) {
                    throw new BizException("角色不存在: id=" + roleId);
                }
                UserRole userRole = new UserRole();
                userRole.setUserId(userId);
                userRole.setRoleId(roleId);
                userRole.setCreateTime(LocalDateTime.now());
                repositories.getUserRole().insert(userRole);
            }
        }
    }

    /** 部门归属校验：主属必填且在归属集合内，集合内部门须存在 */
    private void validateOrgRelations(List<Long> orgIds, Long primaryOrgId) {
        if (primaryOrgId == null) {
            throw new BizException("请选择主属部门");
        }
        if (orgIds == null || orgIds.isEmpty()) {
            throw new BizException("请至少选择一个归属部门");
        }
        if (!orgIds.contains(primaryOrgId)) {
            throw new BizException("主属部门必须在归属部门集合内");
        }
        for (Long orgId : orgIds) {
            if (repositories.getOrg().findById(orgId) == null) {
                throw new BizException("部门不存在: id=" + orgId);
            }
        }
    }

    /** 主管链防成环：沿新主管向上追溯，禁止出现本人 */
    private void checkManagerCycle(Long userId, Long managerUserId) {
        Long cursor = managerUserId;
        while (cursor != null && cursor != 0) {
            if (cursor.equals(userId)) {
                throw new BizException("直属主管不能形成循环汇报关系");
            }
            User manager = repositories.getUser().findById(cursor);
            if (manager == null) {
                throw new BizException("直属主管不存在: id=" + cursor);
            }
            cursor = manager.getManagerUserId();
        }
    }

    private void validateEmployeeNoUnique(String employeeNo, Long excludeUserId) {
        boolean duplicated = repositories.getUser().findAll().stream()
                .anyMatch(u -> employeeNo.equals(u.getEmployeeNo())
                        && !u.getId().equals(excludeUserId));
        if (duplicated) {
            throw new BizException("工号已存在: " + employeeNo);
        }
    }

    private void validateMobileUnique(String mobile, Long excludeUserId) {
        // 手机号选填：为空不参与唯一性校验（2026-09-11 用户需求：新增用户不填手机号）
        if (mobile == null || mobile.isBlank()) {
            return;
        }
        boolean duplicated = repositories.getUser().findAll().stream()
                .anyMatch(u -> mobile.equals(u.getMobile())
                        && !u.getId().equals(excludeUserId));
        if (duplicated) {
            throw new BizException("手机号已存在: " + mobile);
        }
    }

    private void validateUsernameUnique(String username, Long excludeUserId) {
        if (username == null || username.isBlank()) {
            throw new BizException("登录账号不能为空");
        }
        boolean duplicated = repositories.getAccount().findAll().stream()
                .anyMatch(a -> username.equals(a.getUsername())
                        && !a.getUserId().equals(excludeUserId));
        if (duplicated) {
            throw new BizException("登录账号已存在: " + username);
        }
    }

    private User requireUser(Long id) {
        User user = repositories.getUser().findById(id);
        if (user == null) {
            throw new BizException("用户不存在: id=" + id);
        }
        return user;
    }

    private Account findAccount(Long userId) {
        return repositories.getAccount().findAll().stream()
                .filter(a -> userId.equals(a.getUserId()))
                .findFirst().orElse(null);
    }

    /** 通用列筛选白名单（表头漏斗字段，2026-09-10） */
    private static final Map<String, java.util.function.Function<UserListItem, Object>> USER_FILTER_FIELDS = Map.ofEntries(
            Map.entry("name", UserListItem::name),
            Map.entry("employeeNo", UserListItem::employeeNo),
            Map.entry("mobile", UserListItem::mobile),
            Map.entry("username", UserListItem::username),
            Map.entry("managerName", UserListItem::managerName),
            Map.entry("status", UserListItem::status),
            Map.entry("createTime", UserListItem::createTime),
            Map.entry("orgs", item -> item.orgs().stream().map(UserOrgItem::orgName).toList()),
            Map.entry("roleNames", UserListItem::roleNames));

    /** 部门归属展示（主属在前） */
    private List<UserOrgItem> orgItems(Long userId) {
        Map<Long, String> orgNameById = repositories.getOrg().findAll().stream()
                .collect(Collectors.toMap(Org::getId, Org::getName, (a, b) -> a));
        List<UserOrgItem> items = new ArrayList<>();
        Optional<UserOrg> primary = repositories.getUserOrg().findAll().stream()
                .filter(uo -> userId.equals(uo.getUserId()) && Boolean.TRUE.equals(uo.getIsPrimary()))
                .findFirst();
        primary.ifPresent(uo -> items.add(new UserOrgItem(
                uo.getOrgId(), orgNameById.getOrDefault(uo.getOrgId(), ""), true)));
        repositories.getUserOrg().findAll().stream()
                .filter(uo -> userId.equals(uo.getUserId()) && !Boolean.TRUE.equals(uo.getIsPrimary()))
                .forEach(uo -> items.add(new UserOrgItem(
                        uo.getOrgId(), orgNameById.getOrDefault(uo.getOrgId(), ""), false)));
        return items;
    }

    private UserListItem toListItem(User user, Map<Long, Account> accountByUserId,
                                    Map<Long, String> userNameById, Map<Long, String> roleNameById) {
        Account account = accountByUserId.get(user.getId());
        List<UserOrgItem> orgs = orgItems(user.getId());
        List<String> roleNames = repositories.getUserRole().findAll().stream()
                .filter(ur -> user.getId().equals(ur.getUserId()))
                .map(UserRole::getRoleId)
                .distinct()
                .map(roleNameById::get)
                .filter(Objects::nonNull)
                .toList();
        return new UserListItem(
                user.getId(),
                user.getName(),
                user.getEmployeeNo(),
                user.getMobile(),
                user.getEmail(),
                user.getManagerUserId(),
                user.getManagerUserId() == null ? "" : userNameById.getOrDefault(user.getManagerUserId(), ""),
                user.getStatus(),
                account == null ? "" : account.getUsername(),
                orgs,
                roleNames,
                isOrgLeader(user.getId(), orgs),
                user.getCreateTime()
        );
    }

    /** 判权：系统管理类接口按权限点校验（2026-09-10 权限审计修复） */
    private void requirePerm(String code) {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), code)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
    }


    /** 用户日志：对象格式 名称(id)，与其他模块一致 */
    private void logUser(String action, User user, String detail) {
        logService.addLogCurrentUser("user", user.getName() + "(" + user.getId() + ")", action, detail);
    }

    /** 该用户是否为其所属任一机构的负责人（组织管理页「身份」列） */
    private boolean isOrgLeader(Long userId, List<UserOrgItem> orgs) {
        if (orgs == null || orgs.isEmpty()) {
            return false;
        }
        Set<Long> orgIds = orgs.stream().map(UserOrgItem::orgId).collect(Collectors.toSet());
        return repositories.getOrg().findAll().stream()
                .anyMatch(o -> orgIds.contains(o.getId()) && userId.equals(o.getLeaderUserId()));
    }

    /**
     * 组织管理页的「创建属性」（2026-09-11 用户需求）：
     * 管理者 → 同时把该用户写为所选机构的负责人；下级成员 → 直属主管默认为该机构负责人。
     */
    private void applyOrgRole(UserSaveRequest request, User user) {
        if (request.primaryOrgId() == null || request.asOrgLeader() == null) {
            return;
        }
        Org org = repositories.getOrg().findById(request.primaryOrgId());
        if (org == null) {
            return;
        }
        if (Boolean.TRUE.equals(request.asOrgLeader())) {
            org.setLeaderUserId(user.getId());
            org.setUpdateTime(LocalDateTime.now());
            repositories.getOrg().updateById(org);
        } else if (request.managerUserId() == null && org.getLeaderUserId() != null
                && !org.getLeaderUserId().equals(user.getId())) {
            user.setManagerUserId(org.getLeaderUserId());
            user.setUpdateTime(LocalDateTime.now());
            repositories.getUser().updateById(user);
        }
    }
}