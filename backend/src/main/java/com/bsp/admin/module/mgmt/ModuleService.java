package com.bsp.admin.module.mgmt;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.dict.DictService;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.mgmt.domain.Module;
import com.bsp.admin.module.mgmt.dto.ModuleSaveRequest;
import com.bsp.admin.module.project.domain.Project;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 模块服务：列表筛选、详情（统计/成员/日志/挂靠项目）、级联删除、状态自动流转（待办>0→活跃）、CSV 导入导出。
 *
 * <p>任务范围口径（2026-09-11 修订）：模块任务 = 直属任务（挂靠概念已移除）；
 * 列表待办数、详情统计均同源同口径；可见性 = 参与者（负责人/研发/测试）及其上级。</p>
 */
@Service
@RequiredArgsConstructor
public class ModuleService {

    /** 归属产品字典 code */
    public static final String DICT_PRODUCT = "product_belong";
    /** 模块分类字典 code */
    public static final String DICT_CATEGORY = "module_type";
    /** 模块/专题状态字典 code */
    public static final String DICT_STATUS = "module_status";
    /** 状态初始值：新建模块默认「非活跃」（2026-09-11 用户需求：状态由任务待办决定，表单不再填写） */
    private static final String STATUS_INACTIVE = "非活跃";

    private final Repositories repositories;
    private final PermissionService permissionService;
    private final LogService logService;
    private final DictService dictService;

    // ==================== 查询 ====================

    /**
     * 模块列表（FR-MOD-001/002）：
     * - 渲染前强制同步状态（FR-TASK-014）；
     * - 筛选：名称模糊、产品/分类/部门/小组/负责人/状态精确；scope=mine 仅本人负责的模块；
     * - 派生字段：todoCount（含挂靠项目任务）、canEdit、belongProjects；
     * - size <= 0 表示全量（导出用）。
     */
    public PageResult<Map<String, Object>> list(int current, int size, String kw,
                                                 List<String> product, List<String> category, List<String> dept,
                                                 List<String> team, List<String> owner, List<String> status,
                                                 String scope, Map<String, RowFilters.Condition> filters) {
        requireView();
        syncModuleStatus();
        boolean mine = "mine".equalsIgnoreCase(scope);
        String myName = mine ? permissionService.currentUserName() : null;

        List<Module> matched = repositories.getModule().findAll().stream()
                .filter(m -> kw == null || kw.isBlank() || (m.getName() != null && m.getName().contains(kw)))
                .filter(m -> matchAny(product, m.getProduct()))
                .filter(m -> matchAny(category, m.getCategory()))
                .filter(m -> matchAny(dept, m.getDept()))
                .filter(m -> matchAny(team, m.getTeam()))
                .filter(m -> matchAny(owner, m.getOwner()))
                .filter(m -> matchAny(status, m.getStatus()))
                .filter(m -> !mine || (myName != null && myName.equals(m.getOwner())))
                .sorted(Comparator.comparing(Module::getId))
                .toList();

        Map<String, Long> todoByModule = todoCountByModule();
        boolean canEdit = permissionService.hasApiPerm(AuthContext.getUserId(), "module:edit");
        // 数据权限（2026-09-11 用户需求）：仅「参与者及其上级」可见；系统管理员（module:view-all）全量
        boolean viewAll = permissionService.hasApiPerm(AuthContext.getUserId(), "module:view-all");
        Set<String> participantNames = viewAll ? Set.of() : visibleParticipantNames();

        List<Map<String, Object>> rows = matched.stream()
                .filter(m -> viewAll || participants(m).stream().anyMatch(participantNames::contains))
                .map(m -> toRow(m, todoByModule, canEdit))
                // 通用列筛选（2026-09-10：所有字段均支持表头漏斗，含待办数等派生字段）
                .filter(row -> RowFilters.matchRow(row, filters))
                .toList();
        long total = rows.size();
        int from = size > 0 ? (int) Math.min((current - 1L) * size, total) : 0;
        int to = size > 0 ? (int) Math.min(from + (long) size, total) : (int) total;
        return new PageResult<>(rows.subList(from, to), size > 0 ? current : 0, size, total);
    }

    /** 筛选候选值（FR-MOD-002/FR-V5-015）：产品/分类/状态读字典，部门/小组读组织树，负责人取现有模块 */
    public Map<String, Object> options() {
        requireView();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", dictNames(DICT_PRODUCT));
        result.put("categories", dictNames(DICT_CATEGORY));
        result.put("statuses", dictNames(DICT_STATUS));
        // 2026-09-11 需求：负责部门/负责小组统一取组织管理的全部组织名称（原先按层级切分，组织形状不同会选不到）
        result.put("depts", orgNames());
        result.put("teams", orgNames());
        result.put("owners", repositories.getModule().findAll().stream()
                .map(Module::getOwner)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList());
        return result;
    }

    /** 模块详情（FR-MOD-007）：统计概览（含挂靠项目任务）、挂靠项目、成员（角色字段推导）、操作日志 */
    public Map<String, Object> detail(String id) {
        requireView();
        Module module = requireModule(id);
        syncModuleStatus();
        module = requireModule(id);
        requireModuleVisible(module);

        List<com.bsp.admin.module.task.domain.Task> tasks = moduleTasks(id);
        long total = tasks.size();
        long done = tasks.stream().filter(t -> "已完成".equals(t.getStatus())).count();
        long todo = total - done;
        long docCount = repositories.getDoc().findAll().stream()
                .filter(d -> id.equals(d.getModuleId()))
                .count();

        Map<String, Object> result = toRow(module, todoCountByModule(),
                permissionService.hasApiPerm(AuthContext.getUserId(), "module:edit"));
        result.put("totalTaskCount", total);
        result.put("doneTaskCount", done);
        result.put("todoCount", todo);
        result.put("completionRate", total == 0 ? 0 : Math.round(done * 100.0 / total));
        result.put("docCount", docCount);
        result.put("members", members(module, tasks));
        result.put("logs", repositories.getLog().findAll().stream()
                .filter(l -> "module".equals(l.getModule()))
                .filter(l -> l.getTarget() != null && l.getTarget().endsWith("(" + id + ")"))
                .sorted(Comparator.comparing(com.bsp.admin.module.log.domain.Log::getTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(l -> {
                    Map<String, Object> item = new LinkedHashMap<String, Object>();
                    item.put("time", l.getTime() == null ? null
                            : l.getTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                    item.put("operator", l.getOperator());
                    item.put("action", l.getAction());
                    item.put("detail", l.getDetail());
                    return item;
                })
                .toList());
        return result;
    }

    // ==================== 写操作 ====================

    /** 新增（字典值校验：非法/停用项拒绝） */
    public void create(ModuleSaveRequest request) {
        requireEdit();
        validateDictValues(request);
        if (findByName(request.name().trim()) != null) {
            throw new BizException("已存在同名模块：" + request.name());
        }
        Module module = new Module();
        module.setId(nextId());
        applyFields(module, request);
        if (empty(module.getStatus())) {
            // 默认非活跃；有未完成任务时由 syncModuleStatus 自动转为活跃
            module.setStatus(STATUS_INACTIVE);
        }
        module.setCreateTime(LocalDateTime.now());
        module.setUpdateTime(LocalDateTime.now());
        repositories.getModule().insert(module);
        logModule(module, "新增", "—");
    }

    /** 编辑 */
    public void update(ModuleSaveRequest request) {
        requireEdit();
        Module module = requireModule(request.id());
        validateDictValues(request);
        Module sameName = findByName(request.name().trim());
        if (sameName != null && !sameName.getId().equals(module.getId())) {
            throw new BizException("已存在同名模块：" + request.name());
        }
        String old = brief(module);
        applyFields(module, request);
        module.setUpdateTime(LocalDateTime.now());
        repositories.getModule().updateById(module);
        logModule(module, "编辑", old + " → " + brief(module));
        // 编辑后口径可能变化，立即按待办重新流转
        syncModuleStatus();
    }

    /** 删除（FR-MOD-005）：级联删除直属任务与文档、清空挂靠项目的引用；返回级联数量 */
    public Map<String, Object> delete(String id) {
        requireEdit();
        Module module = requireModule(id);
        List<com.bsp.admin.module.task.domain.Task> tasks = repositories.getTask().findAll().stream()
                .filter(t -> id.equals(t.getModuleId()))
                .toList();
        long docCount = repositories.getDoc().findAll().stream()
                .filter(d -> id.equals(d.getModuleId()))
                .count();
        tasks.forEach(t -> repositories.getTask().deleteById(t.getId()));
        repositories.getDoc().findAll().stream()
                .filter(d -> id.equals(d.getModuleId()))
                .forEach(d -> repositories.getDoc().deleteById(d.getId()));
        repositories.getModule().deleteById(id);
        logModule(module, "删除",
                "级联删除任务 " + tasks.size() + " 个、文档 " + docCount + " 个");
        return Map.of("tasks", tasks.size(), "docs", docCount);
    }

    // ==================== 状态自动流转（FR-TASK-014） ====================

    /** 待办>0 → 活跃；=0 → 非活跃（每次流转写日志；渲染前调用） */
    public void syncModuleStatus() {
        Map<String, Long> todoByModule = todoCountByModule();
        for (Module module : repositories.getModule().findAll()) {
            long todo = todoByModule.getOrDefault(module.getId(), 0L);
            String expected = todo > 0 ? "活跃" : "非活跃";
            if (!expected.equals(module.getStatus())) {
                String old = module.getStatus();
                module.setStatus(expected);
                module.setUpdateTime(LocalDateTime.now());
                repositories.getModule().updateById(module);
                logModule(module, "状态自动流转", (old == null ? "—" : old) + " → " + expected);
            }
        }
    }

    // ==================== CSV 导入导出（FR-MOD-006） ====================

    /** Excel 表头（导出/导入共用映射基准） */
    public List<String> excelHeaders() {
        return List.of("模块名称", "归属产品", "模块分类", "负责部门", "负责小组",
                "模块负责人", "研发负责人", "测试负责人", "成本对象", "模块状态", "描述");
    }

    /** 导出行数据（xlsx 由控制器负责组装；列筛选与列表同口径） */
    public List<List<String>> excelRows(String kw, List<String> product, List<String> category, List<String> dept,
                                      List<String> team, List<String> owner, List<String> status, String scope,
                                      Map<String, RowFilters.Condition> filters) {
        requireView();
        syncModuleStatus();
        boolean mine = "mine".equalsIgnoreCase(scope);
        String myName = mine ? permissionService.currentUserName() : null;
        Map<String, Long> todoByModule = todoCountByModule();
        boolean canEdit = permissionService.hasApiPerm(AuthContext.getUserId(), "module:edit");
        boolean viewAll = permissionService.hasApiPerm(AuthContext.getUserId(), "module:view-all");
        Set<String> participantNames = viewAll ? Set.of() : visibleParticipantNames();
        return repositories.getModule().findAll().stream()
                .filter(m -> kw == null || kw.isBlank() || (m.getName() != null && m.getName().contains(kw)))
                .filter(m -> matchAny(product, m.getProduct()))
                .filter(m -> matchAny(category, m.getCategory()))
                .filter(m -> matchAny(dept, m.getDept()))
                .filter(m -> matchAny(team, m.getTeam()))
                .filter(m -> matchAny(owner, m.getOwner()))
                .filter(m -> matchAny(status, m.getStatus()))
                .filter(m -> !mine || (myName != null && myName.equals(m.getOwner())))
                .filter(m -> viewAll || participants(m).stream().anyMatch(participantNames::contains))
                .sorted(Comparator.comparing(Module::getId))
                .map(m -> toRow(m, todoByModule, canEdit))
                .filter(row -> RowFilters.matchRow(row, filters))
                .map(row -> List.of(
                        cell(row.get("name")), cell(row.get("product")), cell(row.get("category")),
                        cell(row.get("dept")), cell(row.get("team")), cell(row.get("owner")),
                        row.get("rd") == null ? "" : String.join("、", (List<String>) row.get("rd")),
                        cell(row.get("test")), cell(row.get("cost")), cell(row.get("status")), cell(row.get("desc"))))
                .toList();
    }

    /** 导出单元格取值（null → 空串） */
    @SuppressWarnings("unchecked")
    private String cell(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 导入（按表头映射批量创建）：
     * - 缺模块名称或与存量同名 → 整行跳过；
     * - 字典字段非法/停用值忽略（置空），其余字段原样入库；
     * - 返回 created / skipped。
     */
    public Map<String, Object> importRows(List<Map<String, String>> rows) {
        requireEdit();
        Set<String> productNames = new HashSet<>(dictNames(DICT_PRODUCT));
        Set<String> categoryNames = new HashSet<>(dictNames(DICT_CATEGORY));
        Set<String> statusNames = new HashSet<>(dictNames(DICT_STATUS));
        int created = 0;
        int skipped = 0;
        for (Map<String, String> row : rows) {
            String name = trimToNull(row.get("模块名称"));
            if (name == null || findByName(name) != null) {
                skipped++;
                continue;
            }
            Module module = new Module();
            module.setId(nextId());
            module.setName(name);
            // 非法字典值忽略（置空）
            module.setProduct(pickIfValid(trimToNull(row.get("归属产品")), productNames));
            module.setCategory(pickIfValid(trimToNull(row.get("模块分类")), categoryNames));
            module.setDept(trimToNull(row.get("负责部门")));
            module.setTeam(trimToNull(row.get("负责小组")));
            module.setOwner(trimToNull(row.get("模块负责人")));
            String rd = trimToNull(row.get("研发负责人"));
            module.setRd(rd == null ? List.of() : splitNames(rd));
            module.setTest(trimToNull(row.get("测试负责人")));
            module.setCost(trimToNull(row.get("成本对象")));
            module.setStatus(pickIfValid(trimToNull(row.get("模块状态")), statusNames));
            module.setDesc(trimToNull(row.get("描述")));
            module.setCreateTime(LocalDateTime.now());
            module.setUpdateTime(LocalDateTime.now());
            repositories.getModule().insert(module);
            logModule(module, "导入", "CSV 批量导入");
            created++;
        }
        syncModuleStatus();
        return Map.of("created", created, "skipped", skipped);
    }

    // ==================== 内部工具 ====================

    private void requireView() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "module:view")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无模块查看权限");
        }
    }

    private void requireEdit() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "module:edit")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无模块编辑权限");
        }
    }

    private void validateDictValues(ModuleSaveRequest request) {
        if (!dictNames(DICT_PRODUCT).contains(request.product())) {
            throw new BizException("非法或已停用的归属产品：" + request.product());
        }
        if (!dictNames(DICT_CATEGORY).contains(request.category())) {
            throw new BizException("非法或已停用的模块分类：" + request.category());
        }
        // 状态由任务待办自动流转，表单不再填写：仅在显式传入时才校验字典值
        if (!empty(request.status()) && !dictNames(DICT_STATUS).contains(request.status())) {
            throw new BizException("非法或已停用的模块状态：" + request.status());
        }
    }

    private void applyFields(Module module, ModuleSaveRequest request) {
        module.setName(request.name().trim());
        module.setProduct(request.product());
        module.setCategory(request.category());
        module.setDept(request.dept());
        module.setTeam(request.team());
        module.setOwner(request.owner().trim());
        module.setRd(request.rd() == null ? List.of() : request.rd().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList());
        module.setTest(trimToNull(request.test()));
        module.setCost(trimToNull(request.cost()));
        // 状态不由表单维护：未传则新建置「非活跃」、编辑保持原值（随后由 syncModuleStatus 按待办校正）
        if (!empty(request.status())) {
            module.setStatus(request.status());
        }
        module.setStakeholders(request.stakeholders() == null ? List.of() : request.stakeholders().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList());
        module.setDesc(trimToNull(request.desc()));
    }

    /** 模块任务范围 = 直属任务（2026-09-11 用户需求：挂靠概念移除，不再含挂靠项目任务） */
    private List<com.bsp.admin.module.task.domain.Task> moduleTasks(String moduleId) {
        return repositories.getTask().findAll().stream()
                .filter(t -> moduleId.equals(t.getModuleId()))
                .toList();
    }

    /** 各模块待办数（状态 ≠ 已完成；2026-09-11 起仅统计直属任务） */
    private Map<String, Long> todoCountByModule() {
        Map<String, Long> result = new HashMap<>();
        for (com.bsp.admin.module.task.domain.Task t : repositories.getTask().findAll()) {
            if ("已完成".equals(t.getStatus()) || t.getModuleId() == null) {
                continue;
            }
            result.merge(t.getModuleId(), 1L, Long::sum);
        }
        return result;
    }

    // ==================== 数据权限（2026-09-11 用户需求） ====================

    /** 模块参与者：模块负责人 / 研发负责人 / 测试负责人 */
    private List<String> participants(Module module) {
        List<String> names = new ArrayList<>();
        if (module.getOwner() != null && !module.getOwner().isBlank()) {
            names.add(module.getOwner().trim());
        }
        if (module.getRd() != null) {
            module.getRd().stream().filter(n -> n != null && !n.isBlank()).map(String::trim).forEach(names::add);
        }
        if (module.getTest() != null && !module.getTest().isBlank()) {
            names.add(module.getTest().trim());
        }
        return names;
    }

    /** 当前用户可见的参与者姓名集合 = 本人 + 主管链子树（本人所有下级） */
    private Set<String> visibleParticipantNames() {
        Set<String> names = new HashSet<>();
        Long me = AuthContext.getUserId();
        if (me == null) {
            return names;
        }
        Map<Long, String> nameById = new HashMap<>();
        repositories.getUser().findAll().forEach(u -> nameById.put(u.getId(), u.getName()));
        Set<Long> scope = new HashSet<>();
        scope.add(me);
        boolean grew = true;
        while (grew) {
            grew = false;
            for (com.bsp.admin.module.user.domain.User u : repositories.getUser().findAll()) {
                if (u.getManagerUserId() != null && scope.contains(u.getManagerUserId()) && scope.add(u.getId())) {
                    grew = true;
                }
            }
        }
        scope.forEach(userId -> {
            String name = nameById.get(userId);
            if (name != null && !name.isBlank()) {
                names.add(name.trim());
            }
        });
        return names;
    }

    /** 详情可见性校验：非管理员需为参与者或参与者上级 */
    private void requireModuleVisible(Module module) {
        if (permissionService.hasApiPerm(AuthContext.getUserId(), "module:view-all")) {
            return;
        }
        Set<String> visibleNames = visibleParticipantNames();
        if (participants(module).stream().noneMatch(visibleNames::contains)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该模块（仅参与者及其上级可见）");
        }
    }

    /** 模块成员（2026-09-11 用户需求：仅 模块负责人 / 研发负责人 / 测试负责人） */
    private List<Map<String, Object>> members(Module module, List<com.bsp.admin.module.task.domain.Task> tasks) {
        Map<String, Set<String>> roleByMember = new LinkedHashMap<>();
        addRole(roleByMember, module.getOwner(), "模块负责人");
        if (module.getRd() != null) {
            module.getRd().forEach(name -> addRole(roleByMember, name, "研发负责人"));
        }
        addRole(roleByMember, module.getTest(), "测试负责人");
        return roleByMember.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "roles", List.copyOf(e.getValue())))
                .toList();
    }

    private void addRole(Map<String, Set<String>> roleByMember, String name, String role) {
        if (name == null || name.isBlank()) {
            return;
        }
        roleByMember.computeIfAbsent(name.trim(), k -> new java.util.LinkedHashSet<>()).add(role);
    }

    private Map<String, Object> toRow(Module m, Map<String, Long> todoByModule, boolean canEdit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", m.getId());
        row.put("name", m.getName());
        row.put("product", m.getProduct());
        row.put("category", m.getCategory());
        row.put("dept", m.getDept());
        row.put("team", m.getTeam());
        row.put("owner", m.getOwner());
        row.put("rd", m.getRd());
        row.put("test", m.getTest());
        row.put("cost", m.getCost());
        row.put("status", m.getStatus());
        row.put("stakeholders", m.getStakeholders());
        row.put("desc", m.getDesc());
        row.put("todoCount", todoByModule.getOrDefault(m.getId(), 0L));
        row.put("canEdit", canEdit);
        row.put("createTime", m.getCreateTime());
        row.put("updateTime", m.getUpdateTime());
        return row;
    }

    /** 日志操作对象格式：名称(id)，与状态自动流转日志一致（详情页按后缀过滤） */
    private void logModule(Module module, String action, String detail) {
        logService.addLogCurrentUser("module", module.getName() + "(" + module.getId() + ")", action, detail);
    }

    private String brief(Module module) {
        return module.getName() + " / " + nz(module.getOwner()) + " / " + nz(module.getStatus());
    }

    private Module requireModule(String id) {
        Module module = repositories.getModule().findById(id);
        if (module == null) {
            throw new BizException("模块不存在");
        }
        return module;
    }

    private Module findByName(String name) {
        return repositories.getModule().findAll().stream()
                .filter(m -> name.equals(m.getName()))
                .findFirst()
                .orElse(null);
    }

    private String nextId() {
        AtomicLong max = new AtomicLong(0);
        repositories.getModule().findAll().forEach(m -> {
            if (m.getId() != null && m.getId().startsWith("m")) {
                try {
                    max.set(Math.max(max.get(), Long.parseLong(m.getId().substring(1))));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        return "m" + (max.get() + 1);
    }

    private List<String> dictNames(String dictCode) {
        return dictService.options(dictCode).stream()
                .map(com.bsp.admin.module.dict.domain.DictItem::getName)
                .toList();
    }

    private String pickIfValid(String value, Set<String> validNames) {
        return value != null && validNames.contains(value) ? value : null;
    }

    /** 多值字段拆分（导出以「、」连接；导入兼容 、，,;；| 分隔） */
    private List<String> splitNames(String joined) {
        return java.util.Arrays.stream(joined.split("[、，,;；|\\n]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    private boolean empty(String s) {
        return s == null || s.isBlank();
    }

    /** 多选筛选命中判定：候选为空 = 不筛选；否则值 ∈ 候选集合（2026-09-10 表头筛选支持多选） */
    private boolean matchAny(List<String> candidates, String value) {
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }
        return value != null && candidates.contains(value);
    }

    private String trimToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private String nz(String s) {
        return s == null ? "" : s;
    }

    /** 组织名称清单（负责部门 / 负责小组候选，2026-09-11 需求：改读组织管理全部节点，不再按层级切分） */
    private List<String> orgNames() {
        return repositories.getOrg().findAll().stream()
                .filter(o -> Integer.valueOf(1).equals(o.getStatus()))
                .sorted(Comparator.comparing((com.bsp.admin.module.org.domain.Org o) -> o.getSort() == null ? 0 : o.getSort())
                        .thenComparing(com.bsp.admin.module.org.domain.Org::getId))
                .map(com.bsp.admin.module.org.domain.Org::getName)
                .toList();
    }

    /**
     * 批量删除（2026-09-11 用户需求）：**仅超级管理员**（权限点 `batch:delete`，只绑 R_SUPER）。
     * 逐条走单条删除的既有级联逻辑；单条失败（如已被删除）跳过，不阻断其余。
     */
    public Map<String, Object> batchDelete(List<String> ids) {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "batch:delete")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无批量删除权限");
        }
        int deleted = 0;
        List<String> failed = new ArrayList<>();
        for (String id : ids == null ? List.<String>of() : ids) {
            try {
                delete(id);
                deleted++;
            } catch (Exception e) {
                failed.add(id);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("deleted", deleted);
        result.put("failed", failed);
        return result;
    }
}
