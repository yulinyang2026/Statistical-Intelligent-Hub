package com.bsp.admin.module.project;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.dict.DictService;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.mgmt.ModuleService;
import com.bsp.admin.module.mgmt.domain.Module;
import com.bsp.admin.module.project.domain.Project;
import com.bsp.admin.module.project.domain.Subsystem;
import com.bsp.admin.module.project.dto.ProjectSaveRequest;
import com.bsp.admin.module.project.dto.SubsystemSaveRequest;
import com.bsp.admin.module.task.domain.Task;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 项目服务：列表树形（项目 → 子系统）、详情（统计/子系统/成员/日志）、级联删除、子系统维护、CSV 导入导出。
 *
 * <p>口径（FR-PROJ-005/015，2026-09-11 修订）：**项目与子系统的待办相互独立**——项目待办/总数/已完成/完成率
 * 仅统计**直属任务**（未挂子系统），子系统行各计自己的任务；两者不重复计数。
 * 项目状态人工维护，不参与自动流转（与模块/专题不同）。</p>
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    /** 归属产品字典 code */
    public static final String DICT_PRODUCT = "product_belong";
    /** 项目状态字典 code */
    public static final String DICT_STATUS = "project_status";

    /** 重要级别固定枚举 A/B/C/D（02-数据模型） */
    private static final List<String> LEVELS = List.of("A", "B", "C", "D");

    private static final String DONE = "已完成";

    private final Repositories repositories;
    private final PermissionService permissionService;
    private final LogService logService;
    private final DictService dictService;
    /** 项目删除会级联删除任务，需同步模块/专题状态（待办口径同源） */
    private final ModuleService moduleService;

    // ==================== 查询 ====================

    /**
     * 项目列表（FR-PROJ-001/002/003）：顶层项目分页，每行携带子系统 children（树形）；
     * 筛选：名称/成本对象模糊、状态/归属产品/负责人/级别精确；scope=mine 仅本人负责的项目。
     */
    public PageResult<Map<String, Object>> list(int current, int size, String kw, List<String> product,
                                                List<String> status, List<String> owner, List<String> level,
                                                String scope, Map<String, RowFilters.Condition> filters) {
        requireView();
        boolean mine = "mine".equalsIgnoreCase(scope);
        String myName = mine ? permissionService.currentUserName() : null;
        boolean canEdit = permissionService.hasApiPerm(AuthContext.getUserId(), "project:edit");

        Map<String, long[]> projectStats = projectStats();
        Map<String, long[]> subsystemStats = subsystemStats();
        Map<String, String> projectNames = new HashMap<>();
        repositories.getProject().findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));

        List<Project> matched = repositories.getProject().findAll().stream()
                .filter(p -> kw == null || kw.isBlank()
                        || (p.getName() != null && p.getName().contains(kw))
                        || (p.getCost() != null && p.getCost().contains(kw)))
                .filter(p -> matchAny(product, p.getProduct()))
                .filter(p -> matchAny(status, p.getStatus()))
                .filter(p -> matchAny(owner, p.getOwner()))
                .filter(p -> matchAny(level, p.getLevel()))
                .filter(p -> !mine || (myName != null && myName.equals(p.getOwner())))
                .sorted(Comparator.comparing(Project::getId))
                .toList();

        List<Map<String, Object>> rows = matched.stream()
                .map(p -> toRow(p, projectStats, subsystemStats, projectNames, canEdit))
                // 通用列筛选（2026-09-10：所有字段均支持表头漏斗，含待办数等派生字段）
                .filter(row -> RowFilters.matchRow(row, filters))
                .toList();
        long total = rows.size();
        int from = size > 0 ? (int) Math.min((current - 1L) * size, total) : 0;
        int to = size > 0 ? (int) Math.min(from + (long) size, total) : (int) total;
        return new PageResult<>(rows.subList(from, to), size > 0 ? current : 0, size, total);
    }

    /** 筛选候选值：产品/状态读字典，级别固定 A~D，部门/小组读组织树，负责人取现存项目（2026-09-11 起不再提供挂靠候选） */
    public Map<String, Object> options() {
        requireView();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("products", dictNames(DICT_PRODUCT));
        result.put("statuses", dictNames(DICT_STATUS));
        result.put("levels", LEVELS);
        // 2026-09-11 需求：负责部门/负责小组统一取组织管理的全部组织名称（不再按层级切分）
        result.put("depts", orgNames());
        result.put("teams", orgNames());
        result.put("owners", repositories.getProject().findAll().stream()
                .map(Project::getOwner)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList());
        return result;
    }

    /**
     * 项目详情（FR-PROJ-008）：统计概览 + 子系统（含统计）+ 成员（角色字段推导）+ 操作日志。
     *
     * <p>2026-09-11 用户需求：统计概览（待办/总数/已完成/完成率）改为**仅直属任务**口径，
     * 与子系统待办相互独立（原「未关联子系统的任务」提示与派生字段随之下线）。</p>
     */
    public Map<String, Object> detail(String id) {
        requireView();
        Project project = requireProject(id);
        boolean canEdit = permissionService.hasApiPerm(AuthContext.getUserId(), "project:edit");

        List<Task> directTasks = directTasksOfProject(id);
        long total = directTasks.size();
        long done = directTasks.stream().filter(t -> DONE.equals(t.getStatus())).count();
        long todo = total - done;
        long docCount = repositories.getDoc().findAll().stream()
                .filter(d -> id.equals(d.getProjectId()))
                .count();

        Map<String, long[]> subsystemStats = subsystemStats();
        Map<String, String> projectNames = new HashMap<>();
        repositories.getProject().findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        Map<String, Object> result = toRow(project, projectStats(), subsystemStats, projectNames, canEdit);
        result.put("totalTaskCount", total);
        result.put("doneTaskCount", done);
        result.put("todoCount", todo);
        result.put("completionRate", total == 0 ? 0 : Math.round(done * 100.0 / total));
        result.put("docCount", docCount);
        result.put("subsystems", subsystemRows(id, subsystemStats, projectNames));
        // 成员推导仍取项目下全部任务（含子系统任务）的执行人，与待办口径无关
        result.put("members", members(project, tasksOfProject(id)));
        result.put("logs", repositories.getLog().findAll().stream()
                .filter(l -> "project".equals(l.getModule()))
                .filter(l -> l.getTarget() != null && l.getTarget().endsWith("(" + id + ")"))
                .sorted(Comparator.comparing(com.bsp.admin.module.log.domain.Log::getTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(l -> {
                    Map<String, Object> item = new LinkedHashMap<>();
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

    // ==================== 项目写操作 ====================

    /** 新增（字典/级别校验；挂靠对象须存在） */
    public void create(ProjectSaveRequest request) {
        requireEdit();
        validateDictValues(request);
        if (findByName(request.name().trim()) != null) {
            throw new BizException("已存在同名项目：" + request.name());
        }
        Project project = new Project();
        project.setId(nextId());
        applyFields(project, request);
        project.setCreateTime(LocalDateTime.now());
        project.setUpdateTime(LocalDateTime.now());
        repositories.getProject().insert(project);
        logProject(project, "新增", "—");
    }

    /** 编辑（项目状态人工维护，不做自动流转） */
    public void update(ProjectSaveRequest request) {
        requireEdit();
        Project project = requireProject(request.id());
        validateDictValues(request);
        Project sameName = findByName(request.name().trim());
        if (sameName != null && !sameName.getId().equals(project.getId())) {
            throw new BizException("已存在同名项目：" + request.name());
        }
        String old = brief(project);
        applyFields(project, request);
        project.setUpdateTime(LocalDateTime.now());
        repositories.getProject().updateById(project);
        logProject(project, "编辑", old + " → " + brief(project));
    }

    /** 删除（FR-PROJ-005）：级联删除子系统、任务（含子任务）与文档；返回各类数量 */
    public Map<String, Object> delete(String id) {
        requireEdit();
        Project project = requireProject(id);
        List<Subsystem> subsystems = subsystemsOfProject(id);
        List<Task> tasks = tasksOfProject(id);
        List<com.bsp.admin.module.doc.domain.Doc> docs = repositories.getDoc().findAll().stream()
                .filter(d -> id.equals(d.getProjectId()))
                .toList();
        tasks.forEach(t -> repositories.getTask().deleteById(t.getId()));
        docs.forEach(d -> repositories.getDoc().deleteById(d.getId()));
        subsystems.forEach(s -> repositories.getSubsystem().deleteById(s.getId()));
        repositories.getProject().deleteById(id);
        // 任务减少会影响模块/专题待办口径，立即重新流转
        moduleService.syncModuleStatus();
        logProject(project, "删除", "级联删除子系统 " + subsystems.size() + " 个、任务 " + tasks.size()
                + " 个、文档 " + docs.size() + " 个");
        return Map.of("subsystems", subsystems.size(), "tasks", tasks.size(), "docs", docs.size());
    }

    // ==================== 子系统（FR-PROJ-013/015/FR-V5-017） ====================

    /** 子系统清单（GET /api/subsystems，支持 projectId 过滤；带任务统计） */
    public List<Map<String, Object>> subsystemList(String projectId) {
        requireView();
        Map<String, long[]> subsystemStats = subsystemStats();
        Map<String, String> projectNames = new HashMap<>();
        repositories.getProject().findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        if (empty(projectId)) {
            return repositories.getSubsystem().findAll().stream()
                    .sorted(Comparator.comparing(Subsystem::getProjectId, Comparator.nullsLast(Comparator.naturalOrder()))
                            .thenComparing(s -> s.getSort() == null ? 0 : s.getSort()))
                    .map(s -> toSubsystemRow(s, subsystemStats, projectNames))
                    .toList();
        }
        return subsystemRows(projectId, subsystemStats, projectNames);
    }

    /** 新增子系统（POST /api/projects/:id/subsystems） */
    public void createSubsystem(String projectId, SubsystemSaveRequest request) {
        requireEdit();
        requireProject(projectId);
        Subsystem subsystem = new Subsystem();
        subsystem.setId(nextSubsystemId());
        subsystem.setProjectId(projectId);
        applySubsystemFields(subsystem, request);
        subsystem.setCreateTime(LocalDateTime.now());
        subsystem.setUpdateTime(LocalDateTime.now());
        repositories.getSubsystem().insert(subsystem);
        Project project = requireProject(projectId);
        logProject(project, "新增子系统", subsystem.getName() + "(" + subsystem.getId() + ")");
    }

    /** 编辑子系统 */
    public void updateSubsystem(SubsystemSaveRequest request) {
        requireEdit();
        Subsystem subsystem = requireSubsystem(request.id());
        String old = subsystem.getName();
        applySubsystemFields(subsystem, request);
        subsystem.setUpdateTime(LocalDateTime.now());
        repositories.getSubsystem().updateById(subsystem);
        logProject(requireProject(subsystem.getProjectId()), "编辑子系统",
                old + " → " + subsystem.getName() + "(" + subsystem.getId() + ")");
    }

    /**
     * 删除子系统：任务不删除，仅解除关联（subsystemId 置空，任务仍属项目）；
     * 返回受影响任务数供前端二次确认提示。
     */
    public Map<String, Object> deleteSubsystem(String id) {
        requireEdit();
        Subsystem subsystem = requireSubsystem(id);
        List<Task> affected = repositories.getTask().findAll().stream()
                .filter(t -> id.equals(t.getSubsystemId()))
                .toList();
        affected.forEach(t -> {
            t.setSubsystemId(null);
            t.setUpdateTime(LocalDateTime.now());
            repositories.getTask().updateById(t);
        });
        repositories.getSubsystem().deleteById(id);
        logProject(requireProject(subsystem.getProjectId()), "删除子系统",
                subsystem.getName() + "(" + subsystem.getId() + ")（解除任务关联 " + affected.size() + " 个）");
        return Map.of("tasks", affected.size());
    }

    // ==================== CSV 导入导出（FR-PROJ-006/007） ====================

    /** Excel 表头（导出/导入共用映射基准） */
    public List<String> excelHeaders() {
        return List.of("项目名称", "成本对象", "重要级别", "归属产品", "所属产品型谱", "负责部门", "负责小组",
                "销售负责人", "项目经理", "项目负责人", "研发负责人", "测试负责人", "项目状态", "预算(万元)",
                "开始日期", "结束日期", "描述");
    }

    /** 导出行数据（UTF-8 BOM 由控制器负责；挂靠列格式「模块·名称」/「专题·名称」） */
    public List<List<String>> excelRows(String kw, List<String> product, List<String> status, List<String> owner,
                                      List<String> level, String scope, Map<String, RowFilters.Condition> filters) {
        requireView();
        boolean mine = "mine".equalsIgnoreCase(scope);
        String myName = mine ? permissionService.currentUserName() : null;
        Map<String, long[]> projectStats = projectStats();
        Map<String, long[]> subsystemStats = subsystemStats();
        Map<String, String> projectNames = new HashMap<>();
        repositories.getProject().findAll().forEach(p -> projectNames.put(p.getId(), p.getName()));
        return repositories.getProject().findAll().stream()
                .filter(p -> kw == null || kw.isBlank()
                        || (p.getName() != null && p.getName().contains(kw))
                        || (p.getCost() != null && p.getCost().contains(kw)))
                .filter(p -> matchAny(product, p.getProduct()))
                .filter(p -> matchAny(status, p.getStatus()))
                .filter(p -> matchAny(owner, p.getOwner()))
                .filter(p -> matchAny(level, p.getLevel()))
                .filter(p -> !mine || (myName != null && myName.equals(p.getOwner())))
                .sorted(Comparator.comparing(Project::getId))
                .map(p -> toRow(p, projectStats, subsystemStats, projectNames, true))
                // 列筛选与列表同口径（2026-09-10 通用 filters）
                .filter(row -> RowFilters.matchRow(row, filters))
                .map(row -> List.of(
                        cell(row.get("name")), cell(row.get("cost")), cell(row.get("level")), cell(row.get("product")),
                        cell(row.get("productLine")), cell(row.get("dept")), cell(row.get("team")),
                        cell(row.get("sale")), cell(row.get("pm")), cell(row.get("owner")), cell(row.get("rd")),
                        cell(row.get("test")), cell(row.get("status")),
                        row.get("budget") == null ? "" : String.valueOf(row.get("budget")),
                        cell(row.get("startDate")), cell(row.get("endDate")),
                        cell(row.get("desc"))))
                .toList();
    }

    /** 导出单元格取值（null → 空串） */
    private String cell(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 导入（按表头映射批量创建）：
     * - 缺项目名称或与存量同名 → 整行跳过；
     * - 字典/级别非法值忽略（置空），挂靠对象按名称匹配（匹配不到留空）；
     * - 返回 created / skipped。
     */
    public Map<String, Object> importRows(List<Map<String, String>> rows) {
        requireEdit();
        Set<String> productNames = new HashSet<>(dictNames(DICT_PRODUCT));
        Set<String> statusNames = new HashSet<>(dictNames(DICT_STATUS));

        int created = 0;
        int skipped = 0;
        for (Map<String, String> row : rows) {
            String name = trimToNull(row.get("项目名称"));
            if (name == null || findByName(name) != null) {
                skipped++;
                continue;
            }
            Project project = new Project();
            project.setId(nextId());
            project.setName(name);
            project.setCost(trimToNull(row.get("成本对象")));
            String level = trimToNull(row.get("重要级别"));
            project.setLevel(level != null && LEVELS.contains(level) ? level : null);
            project.setProduct(pickIfValid(trimToNull(row.get("归属产品")), productNames));
            project.setProductLine(trimToNull(row.get("所属产品型谱")));
            project.setDept(trimToNull(row.get("负责部门")));
            project.setTeam(trimToNull(row.get("负责小组")));
            project.setSale(trimToNull(row.get("销售负责人")));
            project.setPm(trimToNull(row.get("项目经理")));
            project.setOwner(trimToNull(row.get("项目负责人")));
            project.setRd(trimToNull(row.get("研发负责人")));
            project.setTest(trimToNull(row.get("测试负责人")));
            project.setStatus(pickIfValid(trimToNull(row.get("项目状态")), statusNames));
            project.setBudget(parseDecimal(trimToNull(row.get("预算(万元)"))));
            project.setStartDate(trimToNull(row.get("开始日期")));
            project.setEndDate(trimToNull(row.get("结束日期")));
            project.setDesc(trimToNull(row.get("描述")));
            project.setCreateTime(LocalDateTime.now());
            project.setUpdateTime(LocalDateTime.now());
            repositories.getProject().insert(project);
            logProject(project, "导入", "CSV 批量导入");
            created++;
        }
        return Map.of("created", created, "skipped", skipped);
    }

    // ==================== 内部工具 ====================

    private void requireView() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "project:view")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无项目查看权限");
        }
    }

    private void requireEdit() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "project:edit")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无项目编辑权限");
        }
    }

    private void validateDictValues(ProjectSaveRequest request) {
        if (!dictNames(DICT_PRODUCT).contains(request.product())) {
            throw new BizException("非法或已停用的归属产品：" + request.product());
        }
        if (!dictNames(DICT_STATUS).contains(request.status())) {
            throw new BizException("非法或已停用的项目状态：" + request.status());
        }
        if (request.level() == null || !LEVELS.contains(request.level().trim())) {
            throw new BizException("非法的重要级别：" + request.level());
        }
    }

    private void applyFields(Project project, ProjectSaveRequest request) {
        project.setName(request.name().trim());
        project.setCost(trimToNull(request.cost()));
        project.setLevel(request.level().trim());
        project.setProduct(request.product());
        project.setProductLine(trimToNull(request.productLine()));
        project.setDept(trimToNull(request.dept()));
        project.setTeam(trimToNull(request.team()));
        project.setSale(trimToNull(request.sale()));
        project.setPm(trimToNull(request.pm()));
        project.setOwner(trimToNull(request.owner()));
        project.setRd(trimToNull(request.rd()));
        project.setTest(trimToNull(request.test()));
        project.setStatus(request.status());
        project.setBudget(request.budget());
        project.setStartDate(trimToNull(request.startDate()));
        project.setEndDate(trimToNull(request.endDate()));
        project.setStakeholders(request.stakeholders() == null ? List.of() : request.stakeholders().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList());
        project.setDesc(trimToNull(request.desc()));
    }

    private void applySubsystemFields(Subsystem subsystem, SubsystemSaveRequest request) {
        subsystem.setName(request.name().trim());
        subsystem.setOwner(trimToNull(request.owner()));
        subsystem.setSort(request.sort() == null ? 0 : request.sort());
        // 2026-09-11 用户需求：子系统扩字段
        subsystem.setCost(trimToNull(request.cost()));
        subsystem.setProduct(trimToNull(request.product()));
        subsystem.setDept(trimToNull(request.dept()));
        subsystem.setTeam(trimToNull(request.team()));
        subsystem.setRd(trimToNull(request.rd()));
        subsystem.setPm(trimToNull(request.pm()));
        subsystem.setTest(trimToNull(request.test()));
        subsystem.setStatus(trimToNull(request.status()));
    }

    /** 项目下全部任务（含各子系统任务；用于删除级联与成员推导，不用于待办统计） */
    private List<Task> tasksOfProject(String projectId) {
        return repositories.getTask().findAll().stream()
                .filter(t -> projectId.equals(t.getProjectId()))
                .toList();
    }

    /** 项目**直属任务**（未挂子系统；2026-09-11 用户需求：待办/统计概览口径） */
    private List<Task> directTasksOfProject(String projectId) {
        return repositories.getTask().findAll().stream()
                .filter(t -> projectId.equals(t.getProjectId()))
                .filter(t -> t.getSubsystemId() == null)
                .toList();
    }

    /** 项目维度 [总数, 待办数]（待办 = 状态 ≠ 已完成；**仅直属任务**，子系统任务各计自己的，2026-09-11 用户需求） */
    private Map<String, long[]> projectStats() {
        Map<String, long[]> result = new HashMap<>();
        for (Task t : repositories.getTask().findAll()) {
            if (t.getProjectId() == null || t.getSubsystemId() != null) {
                continue;
            }
            long[] stat = result.computeIfAbsent(t.getProjectId(), k -> new long[2]);
            stat[0]++;
            if (!DONE.equals(t.getStatus())) {
                stat[1]++;
            }
        }
        return result;
    }

    /** 子系统维度 [总数, 待办数]（与项目直属任务口径互斥，两者相加 = 项目下全部任务） */
    private Map<String, long[]> subsystemStats() {
        Map<String, long[]> result = new HashMap<>();
        for (Task t : repositories.getTask().findAll()) {
            if (t.getSubsystemId() == null) {
                continue;
            }
            long[] stat = result.computeIfAbsent(t.getSubsystemId(), k -> new long[2]);
            stat[0]++;
            if (!DONE.equals(t.getStatus())) {
                stat[1]++;
            }
        }
        return result;
    }

    private List<Subsystem> subsystemsOfProject(String projectId) {
        return repositories.getSubsystem().findAll().stream()
                .filter(s -> projectId.equals(s.getProjectId()))
                .sorted(Comparator.comparing(s -> s.getSort() == null ? 0 : s.getSort()))
                .toList();
    }

    private List<Map<String, Object>> subsystemRows(String projectId, Map<String, long[]> subsystemStats,
                                                     Map<String, String> projectNames) {
        return subsystemsOfProject(projectId).stream()
                .map(s -> toSubsystemRow(s, subsystemStats, projectNames))
                .toList();
    }

    /** 子系统行（列表树形 children / 详情子系统 Tab 同源）：含任务总数与待办数（FR-PROJ-015） */
    private Map<String, Object> toSubsystemRow(Subsystem s, Map<String, long[]> subsystemStats,
                                               Map<String, String> projectNames) {
        long[] stat = subsystemStats.getOrDefault(s.getId(), new long[2]);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("nodeType", "subsystem");
        row.put("id", s.getId());
        row.put("name", s.getName());
        row.put("projectId", s.getProjectId());
        row.put("projectName", projectNames.getOrDefault(s.getProjectId(), s.getProjectId()));
        row.put("owner", s.getOwner());
        row.put("sort", s.getSort() == null ? 0 : s.getSort());
        // 2026-09-11 用户需求：子系统扩字段（表格子系统行展示）
        row.put("cost", s.getCost());
        row.put("product", s.getProduct());
        row.put("dept", s.getDept());
        row.put("team", s.getTeam());
        row.put("rd", s.getRd());
        row.put("pm", s.getPm());
        row.put("test", s.getTest());
        row.put("status", s.getStatus());
        row.put("taskCount", stat[0]);
        row.put("todoCount", stat[1]);
        row.put("completionRate", stat[0] == 0 ? 0 : Math.round((stat[0] - stat[1]) * 100.0 / stat[0]));
        row.put("canEdit", permissionService.hasApiPerm(AuthContext.getUserId(), "project:edit"));
        row.put("createTime", s.getCreateTime());
        row.put("updateTime", s.getUpdateTime());
        return row;
    }

    private Map<String, Object> toRow(Project p, Map<String, long[]> projectStats,
                                      Map<String, long[]> subsystemStats,
                                      Map<String, String> projectNames, boolean canEdit) {
        long[] stat = projectStats.getOrDefault(p.getId(), new long[2]);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("nodeType", "project");
        row.put("id", p.getId());
        row.put("name", p.getName());
        row.put("cost", p.getCost());
        row.put("level", p.getLevel());
        row.put("product", p.getProduct());
        row.put("productLine", p.getProductLine());
        row.put("dept", p.getDept());
        row.put("team", p.getTeam());
        row.put("sale", p.getSale());
        row.put("pm", p.getPm());
        row.put("owner", p.getOwner());
        row.put("rd", p.getRd());
        row.put("test", p.getTest());
        row.put("status", p.getStatus());
        row.put("budget", p.getBudget());
        row.put("startDate", p.getStartDate());
        row.put("endDate", p.getEndDate());
        row.put("stakeholders", p.getStakeholders());
        row.put("desc", p.getDesc());
        row.put("taskCount", stat[0]);
        row.put("todoCount", stat[1]);
        row.put("subsystemCount", subsystemsOfProject(p.getId()).size());
        row.put("canEdit", canEdit);
        row.put("createTime", p.getCreateTime());
        row.put("updateTime", p.getUpdateTime());
        row.put("children", subsystemRows(p.getId(), subsystemStats, projectNames));
        return row;
    }

    /** 项目成员推导（角色字段 + 任务执行人自动推导，不建独立成员表，与 05 同口径） */
    private List<Map<String, Object>> members(Project project, List<Task> tasks) {
        Map<String, Set<String>> roleByMember = new LinkedHashMap<>();
        addRole(roleByMember, project.getOwner(), "项目负责人");
        addRole(roleByMember, project.getPm(), "项目经理");
        addRole(roleByMember, project.getSale(), "销售负责人");
        addRole(roleByMember, project.getRd(), "研发负责人");
        addRole(roleByMember, project.getTest(), "测试负责人");
        if (project.getStakeholders() != null) {
            project.getStakeholders().forEach(name -> addRole(roleByMember, name, "干系人"));
        }
        tasks.stream().map(Task::getAssignee).filter(Objects::nonNull)
                .forEach(name -> addRole(roleByMember, name, "任务执行人"));
        return roleByMember.entrySet().stream()
                .map(e -> Map.<String, Object>of("name", e.getKey(), "roles", List.copyOf(e.getValue())))
                .toList();
    }

    private void addRole(Map<String, Set<String>> roleByMember, String name, String role) {
        if (name == null || name.isBlank()) {
            return;
        }
        roleByMember.computeIfAbsent(name.trim(), k -> new LinkedHashSet<>()).add(role);
    }

    /** 日志操作对象格式：名称(id)，与 05 模块一致（详情页按后缀过滤） */
    private void logProject(Project project, String action, String detail) {
        logService.addLogCurrentUser("project", project.getName() + "(" + project.getId() + ")", action, detail);
    }

    private String brief(Project project) {
        return project.getName() + " / " + nz(project.getOwner()) + " / " + nz(project.getStatus());
    }

    private Project requireProject(String id) {
        Project project = repositories.getProject().findById(id);
        if (project == null) {
            throw new BizException("项目不存在");
        }
        return project;
    }

    private Subsystem requireSubsystem(String id) {
        Subsystem subsystem = repositories.getSubsystem().findById(id);
        if (subsystem == null) {
            throw new BizException("子系统不存在");
        }
        return subsystem;
    }

    private Project findByName(String name) {
        return repositories.getProject().findAll().stream()
                .filter(p -> name.equals(p.getName()))
                .findFirst()
                .orElse(null);
    }

    /** 项目主键：存量 p1/p2 沿用，新增按 p+序号递增（不重编存量，避免任务/文档引用悬空） */
    private String nextId() {
        AtomicLong max = new AtomicLong(0);
        repositories.getProject().findAll().forEach(p -> {
            String id = p.getId();
            if (id != null && id.length() > 1 && id.charAt(0) == 'p' && Character.isDigit(id.charAt(1))) {
                try {
                    max.set(Math.max(max.get(), Long.parseLong(id.substring(1))));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        return "p" + (max.get() + 1);
    }

    /** 子系统主键：存量 s1/s2 沿用，新增按 s+序号递增 */
    private String nextSubsystemId() {
        AtomicLong max = new AtomicLong(0);
        repositories.getSubsystem().findAll().forEach(s -> {
            String id = s.getId();
            if (id != null && id.length() > 1 && id.charAt(0) == 's' && Character.isDigit(id.charAt(1))) {
                try {
                    max.set(Math.max(max.get(), Long.parseLong(id.substring(1))));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        return "s" + (max.get() + 1);
    }

    private List<String> dictNames(String dictCode) {
        return dictService.options(dictCode).stream()
                .map(com.bsp.admin.module.dict.domain.DictItem::getName)
                .toList();
    }

    private String pickIfValid(String value, Set<String> validNames) {
        return value != null && validNames.contains(value) ? value : null;
    }

    private BigDecimal parseDecimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
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
