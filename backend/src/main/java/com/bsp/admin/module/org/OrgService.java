package com.bsp.admin.module.org;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;

import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.org.domain.Org;
import com.bsp.admin.module.org.dto.OrgSaveRequest;
import com.bsp.admin.module.org.dto.OrgTransferRequest;
import com.bsp.admin.module.org.dto.OrgTreeNode;
import com.bsp.admin.module.user.domain.User;
import com.bsp.admin.module.user.domain.UserOrg;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 组织机构服务（parent_id + path 描述层级，百级数据量全量重算）
 */
@Service
@RequiredArgsConstructor
public class OrgService {

    /** 建议层级深度上限 */
    private static final int MAX_LEVEL = 10;

    private final Repositories repositories;
    private final PermissionService permissionService;
    /** 操作日志（2026-09-11 用户需求：所有数据变动均需留痕） */
    private final LogService logService;

    /**
     * 部门树（按 sort 升序、id 升序；含负责人名与在职人员数）
     *
     * <p>构建顺序为**深度降序**（先叶子后父级）：父节点组装子列表时，子节点必须已是"已挂好自己子树"的
     * 最终对象。此前实现是"先给全部节点建快照、再逐个挂子节点"，而挂载时取的是**快照期的旧对象**，
     * 导致父节点永远只看到没有下级的子节点——**第 3 层及以下部门会被静默丢弃**（2026-09-11 修复）。</p>
     */
    public List<OrgTreeNode> tree() {
        List<Org> all = repositories.getOrg().findAll();

        // 部门人数（主属 + 兼职，按人员去重）
        Map<Long, Long> userCountByOrg = repositories.getUserOrg().findAll().stream()
                .collect(Collectors.groupingBy(UserOrg::getOrgId, Collectors.counting()));

        Map<Long, String> userNameById = repositories.getUser().findAll().stream()
                .collect(Collectors.toMap(User::getId, User::getName, (a, b) -> a));

        Map<Long, List<Org>> childrenByParent = all.stream()
                .collect(Collectors.groupingBy(Org::getParentId));

        // 深度降序：保证处理父级时其所有子级已完成装配（子级 level 必然大于父级）
        List<Org> deepestFirst = all.stream()
                .sorted(Comparator.comparingInt((Org o) -> o.getLevel() == null ? 0 : o.getLevel()).reversed())
                .toList();

        Map<Long, OrgTreeNode> nodeById = new HashMap<>();
        for (Org org : deepestFirst) {
            List<OrgTreeNode> children = childrenByParent.getOrDefault(org.getId(), List.of()).stream()
                    .map(child -> nodeById.get(child.getId()))
                    .filter(Objects::nonNull)
                    .sorted(NODE_ORDER)
                    .toList();
            nodeById.put(org.getId(), withChildren(toNode(org, userNameById, userCountByOrg), children));
        }
        return childrenByParent.getOrDefault(0L, List.of()).stream()
                .map(org -> nodeById.get(org.getId()))
                .filter(Objects::nonNull)
                .sorted(NODE_ORDER)
                .toList();
    }

    /** 同级排序：sort 升序、id 升序（sort 可空，视为 0） */
    private static final Comparator<OrgTreeNode> NODE_ORDER =
            Comparator.comparingInt((OrgTreeNode n) -> n.sort() == null ? 0 : n.sort())
                    .thenComparingLong(n -> n.id() == null ? 0L : n.id());

    /** 新增部门（id 为空）或修改/移动（id 非空） */
    public void save(OrgSaveRequest request) {
        requirePerm(request.id() == null ? "system:org:create" : "system:org:update");
        if (request.id() == null) {
            create(request);
        } else {
            update(request);
        }
    }

    private void create(OrgSaveRequest request) {
        validateCodeUnique(request.code(), null);
        Org parent = requireOrg(request.parentId());

        Org org = new Org();
        org.setParentId(parent == null ? 0L : parent.getId());
        org.setName(request.name());
        org.setCode(request.code());
        org.setLeaderUserId(request.leaderUserId());
        org.setSort(request.sort() == null ? 0 : request.sort());
        org.setStatus(request.status() == null ? 1 : request.status());
        org.setCreateTime(LocalDateTime.now());
        org.setUpdateTime(LocalDateTime.now());
        repositories.getOrg().insert(org);

        // path 依赖生成的 id：先落库拿到 id，再回写 path
        org.setLevel(parent == null ? 1 : parent.getLevel() + 1);
        checkDepth(org.getLevel());
        org.setPath(parent == null ? String.valueOf(org.getId())
                : parent.getPath() + "/" + org.getId());
        repositories.getOrg().updateById(org);
        logOrg("新增部门", org.getName() + "(" + org.getId() + ")",
                "编码 " + org.getCode() + " / 上级 " + (parent == null ? "顶级" : parent.getName())
                        + " / 层级 " + org.getLevel());
    }

    private void update(OrgSaveRequest request) {
        Org org = requireOrg(request.id());
        validateCodeUnique(request.code(), org.getId());

        Long newParentId = request.parentId();
        boolean moved = !Objects.equals(org.getParentId(), newParentId);
        if (moved) {
            Org newParent = requireOrg(newParentId);
            // 禁止移动到自己或自己的子孙节点下
            if (newParent != null && (newParent.getId().equals(org.getId())
                    || (newParent.getPath() + "/").startsWith(org.getPath() + "/"))) {
                throw new BizException("不能移动到本部门或其子部门下");
            }
            org.setParentId(newParent == null ? 0L : newParent.getId());
        }
        String before = brief(org);
        org.setName(request.name());
        org.setCode(request.code());
        org.setLeaderUserId(request.leaderUserId());
        org.setSort(request.sort() == null ? 0 : request.sort());
        org.setStatus(request.status() == null ? 1 : request.status());
        org.setUpdateTime(LocalDateTime.now());
        repositories.getOrg().updateById(org);

        if (moved) {
            recomputeSubtree(org);
        }
        logOrg(moved ? "移动部门" : "编辑部门", org.getName() + "(" + org.getId() + ")",
                before + " → " + brief(org));
    }

    /** 删除部门：必须无子部门且无在职人员（含兼职关系） */
    public void delete(Long id) {
        requirePerm("system:org:delete");
        requireOrg(id);
        boolean hasChildren = repositories.getOrg().findAll().stream()
                .anyMatch(o -> id.equals(o.getParentId()));
        if (hasChildren) {
            throw new BizException("存在子部门，请先转移或删除子部门");
        }
        boolean hasUsers = repositories.getUserOrg().findAll().stream()
                .anyMatch(uo -> id.equals(uo.getOrgId()));
        if (hasUsers) {
            throw new BizException("部门下存在人员，请先转移人员");
        }
        Org org = requireOrg(id);
        repositories.getOrg().deleteById(id);
        logOrg("删除部门", org.getName() + "(" + id + ")", "编码 " + org.getCode());
    }

    /** 批量转移部门人员到目标部门（ORG-04） */
    public void transfer(OrgTransferRequest request) {
        requirePerm("system:org:transfer");
        requireOrg(request.fromOrgId());
        Org target = requireOrg(request.toOrgId());
        if (request.fromOrgId().equals(request.toOrgId())) {
            throw new BizException("源部门与目标部门不能相同");
        }

        List<UserOrg> fromRelations = repositories.getUserOrg().findAll().stream()
                .filter(uo -> request.fromOrgId().equals(uo.getOrgId()))
                .toList();
        if (fromRelations.isEmpty()) {
            throw new BizException("源部门下没有人员");
        }

        for (UserOrg relation : fromRelations) {
            List<UserOrg> userRelations = repositories.getUserOrg().findAll().stream()
                    .filter(uo -> relation.getUserId().equals(uo.getUserId()))
                    .toList();
            UserOrg existing = userRelations.stream()
                    .filter(uo -> request.toOrgId().equals(uo.getOrgId()))
                    .findFirst().orElse(null);

            if (existing != null) {
                // 目标部门已存在关系：删除源关系；若源关系是主属且目标非主属，目标升为主属
                if (Boolean.TRUE.equals(relation.getIsPrimary())
                        && !Boolean.TRUE.equals(existing.getIsPrimary())) {
                    existing.setIsPrimary(true);
                    repositories.getUserOrg().updateById(existing);
                }
                repositories.getUserOrg().deleteById(relation.getId());
            } else {
                relation.setOrgId(target.getId());
                repositories.getUserOrg().updateById(relation);
            }
        }
        logOrg("转移人员", target.getName() + "(" + target.getId() + ")",
                "接收 " + fromRelations.size() + " 人");
    }

    /** 新增/移动/重命名后全量重算整棵子树的 path 与 level（百级数据开销可忽略） */
    private void recomputeSubtree(Org root) {
        root.setLevel(root.getParentId() == 0 ? 1 : requireOrg(root.getParentId()).getLevel() + 1);
        checkDepth(root.getLevel());
        root.setPath(root.getParentId() == 0
                ? String.valueOf(root.getId())
                : requireOrg(root.getParentId()).getPath() + "/" + root.getId());
        repositories.getOrg().updateById(root);

        List<Org> children = repositories.getOrg().findAll().stream()
                .filter(o -> root.getId().equals(o.getParentId()))
                .toList();
        for (Org child : children) {
            child.setParentId(root.getId());
            recomputeSubtree(child);
        }
    }

    private void checkDepth(Integer level) {
        if (level != null && level > MAX_LEVEL) {
            throw new BizException("部门层级深度不能超过 " + MAX_LEVEL + " 级");
        }
    }

    private void validateCodeUnique(String code, Long excludeId) {
        boolean duplicated = repositories.getOrg().findAll().stream()
                .anyMatch(o -> code.equals(o.getCode()) && !o.getId().equals(excludeId));
        if (duplicated) {
            throw new BizException("部门编码已存在: " + code);
        }
    }

    private Org requireOrg(Long id) {
        if (id == null || id == 0) {
            return null;
        }
        Org org = repositories.getOrg().findById(id);
        if (org == null) {
            throw new BizException("部门不存在: id=" + id);
        }
        return org;
    }

    private OrgTreeNode toNode(Org org, Map<Long, String> userNameById, Map<Long, Long> userCountByOrg) {
        return new OrgTreeNode(
                org.getId(),
                org.getParentId(),
                org.getName(),
                org.getCode(),
                org.getLeaderUserId(),
                org.getLeaderUserId() == null ? "" : userNameById.getOrDefault(org.getLeaderUserId(), ""),
                org.getSort(),
                org.getStatus(),
                org.getPath(),
                org.getLevel(),
                userCountByOrg.getOrDefault(org.getId(), 0L),
                org.getCreateTime(),
                org.getUpdateTime(),
                new ArrayList<>()
        );
    }

    private OrgTreeNode withChildren(OrgTreeNode node, List<OrgTreeNode> children) {
        return new OrgTreeNode(node.id(), node.parentId(), node.name(), node.code(), node.leaderUserId(),
                node.leaderName(), node.sort(), node.status(), node.path(), node.level(), node.userCount(),
                node.createTime(), node.updateTime(), children);
    }

    /** 判权：系统管理类接口按权限点校验（2026-09-10 权限审计修复） */
    private void requirePerm(String code) {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), code)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权限执行该操作");
        }
    }


    /** 部门日志：对象格式 名称(id)，与其他模块一致 */
    private void logOrg(String action, String target, String detail) {
        logService.addLogCurrentUser("org", target, action, detail);
    }

    /** 变更摘要（旧值 → 新值 用） */
    private String brief(Org org) {
        return org.getName() + " / " + org.getCode()
                + " / 状态" + (Integer.valueOf(1).equals(org.getStatus()) ? "启用" : "停用");
    }
}