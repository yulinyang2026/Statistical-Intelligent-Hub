package com.bsp.admin.module.task;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.mgmt.ModuleService;
import com.bsp.admin.module.mgmt.domain.Module;
import com.bsp.admin.module.project.domain.Project;
import com.bsp.admin.module.project.domain.Subsystem;
import com.bsp.admin.module.topic.TopicService;
import com.bsp.admin.module.topic.domain.Topic;
import com.bsp.admin.module.task.domain.Task;
import com.bsp.admin.module.task.dto.TaskListItem;
import com.bsp.admin.module.task.dto.TaskPatchRequest;
import com.bsp.admin.module.task.dto.TaskPatchResult;
import com.bsp.admin.module.task.dto.TaskSaveRequest;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 任务服务：两级树、筛选补链、单字段写回（看板拖拽/工时双击）、级联删除、模块/专题状态自动流转。
 */
@Service
@RequiredArgsConstructor
public class TaskService {

    /** 任务状态全集（待办 = 状态 ≠ 已完成） */
    public static final List<String> STATUSES = List.of("待处理", "进行中", "已完成", "受阻");
    /** 优先级全集 */
    public static final List<String> PRIORITIES = List.of("高", "中", "低");

    private static final Pattern MONTH_PATTERN = Pattern.compile("^\\d{4}-\\d{2}$");
    private static final Pattern DATE_PATTERN = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final Repositories repositories;
    private final PermissionService permissionService;
    private final LogService logService;
    private final ModuleService moduleService;
    private final TopicService topicService;

    /**
     * 任务列表（树形，两级）：
     * - 数据范围：无 task:view-all 权限时仅返回 assignee = 当前用户的任务（组员口径，FR-TASK-020）；
     * - 归属/截止月份等筛选时保留匹配任务的父子完整链路（FR-TASK-019）；
     * - 归属筛选多选（OR）：项目/模块/专题各自直属匹配（2026-09-11 起挂靠概念移除）；
     * - 分页针对顶层行（父任务与孤儿任务），子任务随父行内嵌；size <= 0 表示全量（看板视图用，不受分页影响）。
     */
    public PageResult<TaskListItem> list(int current, int size, String kw,
                                         List<String> projectId, List<String> moduleId, List<String> topicId,
                                         String subsystemId,
                                         List<String> status, List<String> priority, List<String> assignee,
                                         List<String> group, String deadline,
                                         Map<String, RowFilters.Condition> filters) {
        Long uid = AuthContext.getUserId();
        if (!permissionService.hasApiPerm(uid, "task:view")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无任务查看权限");
        }
        boolean viewAll = permissionService.hasApiPerm(uid, "task:view-all");
        String myName = permissionService.currentUserName();

        List<Task> all = repositories.getTask().findAll().stream()
                .filter(t -> viewAll || (myName != null && myName.equals(t.getAssignee())))
                .toList();

        // 归属筛选（多选 OR）：项目/模块/专题各自直属匹配（2026-09-11：挂靠概念移除，不再展开挂靠项目任务）
        Set<String> belongProjects = new HashSet<>(nonEmpty(projectId));
        Set<String> modules = new HashSet<>(nonEmpty(moduleId));
        Set<String> topics = new HashSet<>(nonEmpty(topicId));
        boolean hasBelongFilter = !belongProjects.isEmpty() || !modules.isEmpty() || !topics.isEmpty();

        List<Task> matched = all.stream()
                .filter(t -> kw == null || kw.isBlank()
                        || (t.getTitle() != null && t.getTitle().contains(kw))
                        || (t.getDesc() != null && t.getDesc().contains(kw)))
                .filter(t -> !hasBelongFilter
                        || (t.getProjectId() != null && belongProjects.contains(t.getProjectId()))
                        || (t.getModuleId() != null && modules.contains(t.getModuleId()))
                        || (t.getTopicId() != null && topics.contains(t.getTopicId())))
                .filter(t -> empty(subsystemId) || subsystemId.equals(t.getSubsystemId()))
                .filter(t -> matchAny(status, t.getStatus()))
                .filter(t -> matchAny(priority, t.getPriority()))
                .filter(t -> matchAny(assignee, t.getAssignee()))
                .filter(t -> matchAny(group, t.getGroup()))
                .filter(t -> empty(deadline) || deadline.equals(t.getDeadline()))
                // 通用列筛选（2026-09-10：工时/计划完成时间/任务名称等全部字段支持表头漏斗）
                .filter(t -> RowFilters.match(t, filters, TASK_FILTER_FIELDS))
                .toList();

        // 父子链路补回：子任务命中但父任务被过滤时，把父任务补进结果集
        Set<String> matchedIds = matched.stream().map(Task::getId).collect(Collectors.toSet());
        Map<String, Task> byId = all.stream().collect(Collectors.toMap(Task::getId, t -> t, (a, b) -> a));
        Set<String> resultIds = new HashSet<>(matchedIds);
        for (Task t : matched) {
            if (t.getParentId() != null && !matchedIds.contains(t.getParentId())) {
                resultIds.add(t.getParentId());
            }
        }
        List<Task> result = all.stream().filter(t -> resultIds.contains(t.getId())).toList();

        // 构建两级树：分页作用于顶层
        Map<String, List<Task>> childrenByParent = result.stream()
                .filter(t -> t.getParentId() != null)
                .sorted(Comparator.comparing((Task t) -> t.getCreateTime() == null ? "" : t.getCreateTime().toString()))
                .collect(Collectors.groupingBy(Task::getParentId, LinkedHashMap::new, Collectors.toList()));

        // 顶层行 = 无父任务 + 孤儿子任务（父任务不在最终结果集，如组员范围过滤后父任务被范围排除），避免子任务丢失
        Set<String> resultIdSet = result.stream().map(Task::getId).collect(Collectors.toSet());
        List<Task> tops = result.stream()
                .filter(t -> t.getParentId() == null || t.getParentId().isBlank()
                        || !resultIdSet.contains(t.getParentId()))
                .sorted(Comparator.comparing((Task t) -> t.getCreateTime() == null ? "" : t.getCreateTime().toString())
                        .reversed())
                .toList();

        // 补回的父任务可能不在顶层列表中已包含——直接以 tops 为分页基准
        long total = tops.size();
        // size <= 0：全量返回（看板视图），不分页
        int from = size > 0 ? (int) Math.min((current - 1L) * size, total) : 0;
        int to = size > 0 ? (int) Math.min(from + (long) size, total) : (int) total;
        List<TaskListItem> rows = tops.subList(from, to).stream()
                .map(t -> toItem(t, childrenByParent.getOrDefault(t.getId(), List.of()), byId))
                .toList();
        return new PageResult<>(rows, size > 0 ? current : 0, size, total);
    }

    /** 通用列筛选白名单（表头漏斗字段，2026-09-10） */
    private static final Map<String, java.util.function.Function<Task, Object>> TASK_FILTER_FIELDS = Map.ofEntries(
            Map.entry("title", Task::getTitle),
            Map.entry("desc", Task::getDesc),
            Map.entry("assignee", Task::getAssignee),
            Map.entry("priority", Task::getPriority),
            Map.entry("hours", Task::getHours),
            Map.entry("planDate", Task::getPlanDate),
            Map.entry("status", Task::getStatus),
            Map.entry("group", Task::getGroup),
            Map.entry("createTime", Task::getCreateTime),
            Map.entry("updateTime", Task::getUpdateTime));

    /** 筛选候选值（FR-TASK-002 下拉数据源） */
    public Map<String, Object> options() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "task:view")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无任务查看权限");
        }
        List<Task> all = repositories.getTask().findAll();
        Map<String, String> projectName = repositories.getProject().findAll().stream()
                .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));
        Map<String, String> moduleName = repositories.getModule().findAll().stream()
                .collect(Collectors.toMap(Module::getId, Module::getName, (a, b) -> a));
        Map<String, String> topicName = repositories.getTopic().findAll().stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName, (a, b) -> a));

        List<Map<String, String>> belongOptions = new ArrayList<>();
        projectName.forEach((id, name) -> belongOptions.add(Map.of("id", id, "name", name, "type", "project")));
        moduleName.forEach((id, name) -> belongOptions.add(Map.of("id", id, "name", name, "type", "module")));
        topicName.forEach((id, name) -> belongOptions.add(Map.of("id", id, "name", name, "type", "topic")));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("belongOptions", belongOptions);
        result.put("subsystems", repositories.getSubsystem().findAll().stream()
                .map(s -> {
                    Map<String, String> item = new LinkedHashMap<>();
                    item.put("id", s.getId());
                    item.put("name", s.getName());
                    item.put("projectId", s.getProjectId() == null ? "" : s.getProjectId());
                    return item;
                })
                .toList());
        result.put("deadlines", all.stream().map(Task::getDeadline).filter(Objects::nonNull).distinct().sorted().toList());
        result.put("statuses", STATUSES);
        result.put("priorities", PRIORITIES);
        result.put("assignees", all.stream().map(Task::getAssignee).filter(Objects::nonNull).distinct().sorted().toList());
        // 2026-09-11 用户需求：归属小组取组织管理的数据（不再取现有任务里的历史值）
        result.put("groups", repositories.getOrg().findAll().stream()
                .filter(o -> Integer.valueOf(1).equals(o.getStatus()))
                .sorted(Comparator.comparing((com.bsp.admin.module.org.domain.Org o) -> o.getSort() == null ? 0 : o.getSort())
                        .thenComparing(com.bsp.admin.module.org.domain.Org::getId))
                .map(com.bsp.admin.module.org.domain.Org::getName)
                .toList());
        return result;
    }

    /** 新增（子任务继承父任务归属与子系统；层级仅两级） */
    public void create(TaskSaveRequest request) {
        requirePerm("task:create");
        validateCommon(request);
        if (request.parentId() != null && !request.parentId().isBlank()) {
            Task parent = requireTask(request.parentId());
            if (parent.getParentId() != null && !parent.getParentId().isBlank()) {
                throw new BizException("任务层级仅两级，子任务下不能再建子任务");
            }
            // 子任务继承父任务归属与子系统且不可修改
            Task task = buildTask(request, parent.getId());
            task.setProjectId(parent.getProjectId());
            task.setModuleId(parent.getModuleId());
            task.setTopicId(parent.getTopicId());
            task.setSubsystemId(parent.getSubsystemId());
            validateRequiredFields(task);
            repositories.getTask().insert(task);
        } else {
            Task task = buildTask(request, null);
            validateRequiredFields(task);
            repositories.getTask().insert(task);
        }
        syncParentStatuses();
        syncAutoStatus();
    }

    /** 修改（含子任务环检测：父任务不可选择自身或其子孙） */
    public void update(TaskSaveRequest request) {
        Task task = requireTask(request.id());
        validateCommon(request);
        if (request.parentId() != null && !request.parentId().isBlank()) {
            if (request.parentId().equals(task.getId())) {
                throw new BizException("父任务不能是自身");
            }
            // 环检测：父任务不能是自身子孙（两级内 = 自身的子任务）
            Task parent = requireTask(request.parentId());
            if (parent.getParentId() != null && !parent.getParentId().isBlank()) {
                throw new BizException("任务层级仅两级，子任务下不能再建子任务");
            }
            boolean isChildOfSelf = task.getId().equals(parent.getParentId());
            if (isChildOfSelf) {
                throw new BizException("父任务不能是自身的子任务");
            }
            task.setParentId(parent.getId());
            task.setProjectId(parent.getProjectId());
            task.setModuleId(parent.getModuleId());
            task.setTopicId(parent.getTopicId());
            task.setSubsystemId(parent.getSubsystemId());
        } else {
            task.setParentId(null);
            task.setProjectId(blankToNull(request.projectId()));
            task.setModuleId(blankToNull(request.moduleId()));
            task.setTopicId(blankToNull(request.topicId()));
            task.setSubsystemId(blankToNull(request.subsystemId()));
        }
        task.setTitle(request.title().trim());
        task.setDesc(request.desc());
        task.setAssignee(request.assignee().trim());
        task.setPriority(request.priority());
        task.setDeadline(request.deadline());
        task.setHours(request.hours());
        task.setWeek(request.week());
        task.setPlanDate(request.planDate());
        task.setStatus(request.status());
        task.setGroup(request.group());
        task.setUpdateTime(LocalDateTime.now());
        repositories.getTask().updateById(task);
        syncParentStatuses();
        syncAutoStatus();
    }

    /** 单字段更新（看板拖拽与工时双击编辑写回）：权限校验 + 值未变化不写库不写日志 */
    public TaskPatchResult patch(String id, TaskPatchRequest request) {
        Task task = requireTask(id);
        requireCanEdit(task);
        String oldValue = displayValue(task, request.field());
        switch (request.field()) {
            case "status" -> {
                String value = String.valueOf(request.value());
                if (!STATUSES.contains(value)) {
                    throw new BizException("非法的任务状态：" + value);
                }
                task.setStatus(value);
            }
            case "priority" -> {
                String value = String.valueOf(request.value());
                if (!PRIORITIES.contains(value)) {
                    throw new BizException("非法的优先级：" + value);
                }
                task.setPriority(value);
            }
            case "assignee" -> task.setAssignee(String.valueOf(request.value()));
            case "group" -> task.setGroup(String.valueOf(request.value()));
            case "subsystemId" -> task.setSubsystemId(blankToNull(String.valueOf(request.value())));
            case "deadline" -> {
                String value = String.valueOf(request.value());
                if (!MONTH_PATTERN.matcher(value).matches()) {
                    throw new BizException("截止月份格式必须为 YYYY-MM");
                }
                task.setDeadline(value);
            }
            case "planDate" -> {
                String value = String.valueOf(request.value());
                if (value != null && !value.isBlank() && !DATE_PATTERN.matcher(value).matches()) {
                    throw new BizException("计划完成日期格式必须为 YYYY-MM-DD");
                }
                task.setPlanDate(blankToNull(value));
            }
            case "hours" -> {
                int value;
                try {
                    value = Integer.parseInt(String.valueOf(request.value()));
                } catch (NumberFormatException e) {
                    throw new BizException("预计工时必须为整数");
                }
                if (value < 0) {
                    throw new BizException("预计工时不能为负数");
                }
                task.setHours(value);
            }
            default -> throw new BizException("不支持的单字段更新：" + request.field());
        }
        String newValue = displayValue(task, request.field());
        if (Objects.equals(oldValue, newValue)) {
            // 值未变化：不写库、不写日志（08a §7.2）
            return new TaskPatchResult(false);
        }
        task.setUpdateTime(LocalDateTime.now());
        repositories.getTask().updateById(task);
        syncParentStatuses();
        syncAutoStatus();
        // 日志记录本次写回本身的旧值→新值（在 updateById 前取值，避免父任务状态自动推导覆盖显示）
        logService.addLogCurrentUser("task", task.getId() + " " + task.getTitle(),
                "看板写回·" + request.field(), oldValue + " → " + newValue);
        return new TaskPatchResult(true);
    }

    /** 删除（级联删除子任务，返回级联数量） */
    public long delete(String id) {
        requirePerm("task:delete");
        Task task = requireTask(id);
        long cascaded = repositories.getTask().findAll().stream()
                .filter(t -> id.equals(t.getParentId()))
                .count();
        repositories.getTask().findAll().stream()
                .filter(t -> id.equals(t.getParentId()))
                .forEach(child -> repositories.getTask().deleteById(child.getId()));
        repositories.getTask().deleteById(id);
        syncParentStatuses();
        syncAutoStatus();
        return cascaded;
    }

    // ==================== 内部工具 ====================

    /**
     * 编辑权限校验（08a §7.2 / FR-TASK-004）：需 task:edit；
     * 无 task:view-all 时仅可编辑 assignee = 当前用户的任务。
     */
    private void requireCanEdit(Task task) {
        Long uid = AuthContext.getUserId();
        if (!permissionService.hasApiPerm(uid, "task:edit")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无编辑任务权限");
        }
        if (!permissionService.hasApiPerm(uid, "task:view-all")) {
            String myName = permissionService.currentUserName();
            if (myName == null || !myName.equals(task.getAssignee())) {
                throw new BizException(ErrorCode.FORBIDDEN, "仅可编辑分配给自己的任务");
            }
        }
    }

    /** 字段值展示（操作日志与 changed 比较用）：空值统一 「—」 */
    private String displayValue(Task task, String field) {
        Object value = switch (field) {
            case "status" -> task.getStatus();
            case "priority" -> task.getPriority();
            case "assignee" -> task.getAssignee();
            case "group" -> task.getGroup();
            case "subsystemId" -> task.getSubsystemId();
            case "deadline" -> task.getDeadline();
            case "planDate" -> task.getPlanDate();
            case "hours" -> task.getHours();
            default -> null;
        };
        return value == null || String.valueOf(value).isBlank() ? "—" : String.valueOf(value);
    }


    private Task buildTask(TaskSaveRequest request, String parentId) {
        Task task = new Task();
        task.setId(nextId());
        task.setTitle(request.title().trim());
        task.setDesc(request.desc());
        task.setProjectId(blankToNull(request.projectId()));
        task.setModuleId(blankToNull(request.moduleId()));
        task.setTopicId(blankToNull(request.topicId()));
        task.setSubsystemId(blankToNull(request.subsystemId()));
        task.setAssignee(request.assignee().trim());
        task.setPriority(request.priority());
        task.setDeadline(request.deadline());
        task.setHours(request.hours());
        task.setWeek(request.week());
        task.setPlanDate(request.planDate());
        task.setStatus(request.status());
        task.setGroup(request.group());
        task.setParentId(parentId);
        task.setCreateTime(LocalDateTime.now());
        task.setUpdateTime(LocalDateTime.now());
        return task;
    }

    private void validateCommon(TaskSaveRequest request) {
        if (!STATUSES.contains(request.status())) {
            throw new BizException("非法的任务状态：" + request.status());
        }
        if (!PRIORITIES.contains(request.priority())) {
            throw new BizException("非法的优先级：" + request.priority());
        }
        if (request.deadline() != null && !request.deadline().isBlank()
                && !MONTH_PATTERN.matcher(request.deadline()).matches()) {
            throw new BizException("截止月份格式必须为 YYYY-MM");
        }
        if (request.planDate() != null && !request.planDate().isBlank()
                && !DATE_PATTERN.matcher(request.planDate()).matches()) {
            throw new BizException("计划完成日期格式必须为 YYYY-MM-DD");
        }
    }

    private Task requireTask(String id) {
        Task task = repositories.getTask().findById(id);
        if (task == null) {
            throw new BizException("任务不存在");
        }
        return task;
    }

    private String nextId() {
        AtomicLong max = new AtomicLong(0);
        repositories.getTask().findAll().forEach(t -> {
            if (t.getId() != null && t.getId().startsWith("task")) {
                try {
                    max.set(Math.max(max.get(), Long.parseLong(t.getId().substring(4))));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        return "task" + (max.get() + 1);
    }

    /**
     * 父任务状态自动推导：含子任务的任务，状态 = 所有子任务均已完成 → 已完成，否则 → 进行中。
     * （2026-09-09 需求：只要有一个子任务没完成，父任务就是进行中）
     */
    private void syncParentStatuses() {
        List<Task> all = repositories.getTask().findAll();
        Map<String, List<Task>> childrenByParent = all.stream()
                .filter(t -> t.getParentId() != null && !t.getParentId().isBlank())
                .collect(Collectors.groupingBy(Task::getParentId));
        for (Map.Entry<String, List<Task>> entry : childrenByParent.entrySet()) {
            Task parent = repositories.getTask().findById(entry.getKey());
            if (parent == null) {
                continue;
            }
            boolean allDone = entry.getValue().stream().allMatch(t -> "已完成".equals(t.getStatus()));
            String derived = allDone ? "已完成" : "进行中";
            if (!derived.equals(parent.getStatus())) {
                parent.setStatus(derived);
                parent.setUpdateTime(LocalDateTime.now());
                repositories.getTask().updateById(parent);
            }
        }
    }

    /** 模块/专题状态自动流转：待办>0 → 活跃；=0 → 非活跃（仅模块/专题；项目人工维护） */
    private void syncAutoStatus() {
        // 模块/专题分别委托各自 Service（含列表渲染前同步的同一口径，2026-09-11 专题正式实体落地）
        moduleService.syncModuleStatus();
        topicService.syncTopicStatus();
    }

    private TaskListItem toItem(Task t, List<Task> children, Map<String, Task> byId) {
        Map<String, String> projectName = repositories.getProject().findAll().stream()
                .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));
        Map<String, String> moduleName = repositories.getModule().findAll().stream()
                .collect(Collectors.toMap(Module::getId, Module::getName, (a, b) -> a));
        Map<String, String> topicName = repositories.getTopic().findAll().stream()
                .collect(Collectors.toMap(Topic::getId, Topic::getName, (a, b) -> a));
        Map<String, String> subsystemName = repositories.getSubsystem().findAll().stream()
                .collect(Collectors.toMap(Subsystem::getId, Subsystem::getName, (a, b) -> a));

        String belongLabel = null;
        if (t.getProjectId() != null) {
            belongLabel = "项目·" + projectName.getOrDefault(t.getProjectId(), t.getProjectId());
        } else if (t.getModuleId() != null) {
            belongLabel = "模块·" + moduleName.getOrDefault(t.getModuleId(), t.getModuleId());
        } else if (t.getTopicId() != null) {
            belongLabel = "专题·" + topicName.getOrDefault(t.getTopicId(), t.getTopicId());
        }

        List<TaskListItem> childItems = children.stream()
                .map(c -> toItem(c, List.of(), byId))
                .toList();
        return new TaskListItem(t.getId(), t.getTitle(), t.getDesc(), t.getProjectId(), t.getModuleId(),
                t.getTopicId(), t.getSubsystemId(), t.getAssignee(), t.getPriority(), t.getDeadline(),
                t.getHours(), t.getWeek(), t.getPlanDate(), t.getStatus(), t.getGroup(), t.getParentId(),
                belongLabel,
                t.getSubsystemId() == null ? null : subsystemName.getOrDefault(t.getSubsystemId(), null),
                children.size(), childItems, t.getCreateTime(), t.getUpdateTime());
    }

    /** 多选筛选命中判定：候选为空 = 不筛选；否则值 ∈ 候选集合（2026-09-10 表头筛选支持多选） */
    private boolean matchAny(List<String> candidates, String value) {
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }
        return value != null && candidates.contains(value);
    }

    /** 过滤空值后的候选集合（归属筛选用） */
    private List<String> nonEmpty(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().filter(v -> v != null && !v.isBlank()).toList();
    }

    private boolean empty(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
    }
    /** 判权（2026-09-10 权限审计修复：任务新增/删除补齐校验） */
    private void requirePerm(String code) {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), code)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无操作权限");
        }
    }


    /**
     * 新增任务的必填校验（2026-09-11 用户需求）：
     * ① **归属必选**——归属项目/模块/专题至少有一个（原先允许全空 = 计划外临时任务，现按要求收紧）；
     * ② **归属小组必选**。
     * 子任务继承父任务归属，故校验放在继承之后统一进行。
     */
    private void validateRequiredFields(Task task) {
        boolean hasBelong = notBlank(task.getProjectId()) || notBlank(task.getModuleId()) || notBlank(task.getTopicId());
        if (!hasBelong) {
            throw new BizException("请选择任务归属（项目 / 模块 / 专题）");
        }
        if (!notBlank(task.getGroup())) {
            throw new BizException("请选择归属小组");
        }
    }

    private boolean notBlank(String s) {
        return s != null && !s.isBlank();
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