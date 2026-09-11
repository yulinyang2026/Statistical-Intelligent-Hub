package com.bsp.admin.module.topic;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.dict.DictService;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.topic.domain.Topic;
import com.bsp.admin.module.topic.dto.TopicSaveRequest;
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

/**
 * 专题服务（06-专题管理）：列表筛选、详情（统计/成员/日志）、级联删除、状态自动流转（待办>0→活跃）、CSV 导入导出。
 *
 * <p>与 {@link com.bsp.admin.module.mgmt.ModuleService} 同口径实现，差异仅两点：
 * ① 无 product（归属产品）与 category（模块分类）字段；② 主键前缀 tp、数据文件 topic.json。</p>
 *
 * <p>任务范围口径：专题任务 = 直属任务（挂靠概念已移除）；待办 = 状态 ≠ 已完成；
 * 可见性 = 参与者（负责人/研发/测试）及其上级，系统管理员凭 topic:view-all 全量。</p>
 */
@Service
@RequiredArgsConstructor
public class TopicService {

    /** 模块/专题状态字典 code（与模块共用同一字典，06 文档 §1.2） */
    public static final String DICT_STATUS = "module_status";

    private final Repositories repositories;
    private final PermissionService permissionService;
    private final LogService logService;
    private final DictService dictService;

    // ==================== 查询 ====================

    /**
     * 专题列表（FR-TOP-001/002）：
     * - 渲染前强制同步状态（FR-TASK-014）；
     * - 筛选：名称模糊、部门/小组/负责人/状态精确；scope=mine 仅本人负责的专题；
     * - 派生字段：todoCount（直属任务）、canEdit；size <= 0 表示全量（导出用）。
     */
    public PageResult<Map<String, Object>> list(int current, int size, String kw,
                                                List<String> dept, List<String> team, List<String> owner,
                                                List<String> status, String scope,
                                                Map<String, RowFilters.Condition> filters) {
        requireView();
        syncTopicStatus();
        boolean mine = "mine".equalsIgnoreCase(scope);
        String myName = mine ? permissionService.currentUserName() : null;

        List<Topic> matched = repositories.getTopic().findAll().stream()
                .filter(tp -> kw == null || kw.isBlank() || (tp.getName() != null && tp.getName().contains(kw)))
                .filter(tp -> matchAny(dept, tp.getDept()))
                .filter(tp -> matchAny(team, tp.getTeam()))
                .filter(tp -> matchAny(owner, tp.getOwner()))
                .filter(tp -> matchAny(status, tp.getStatus()))
                .filter(tp -> !mine || (myName != null && myName.equals(tp.getOwner())))
                .sorted(Comparator.comparing(Topic::getId))
                .toList();

        Map<String, Long> todoByTopic = todoCountByTopic();
        boolean canEdit = permissionService.hasApiPerm(AuthContext.getUserId(), "topic:edit");
        // 数据权限：仅「参与者及其上级」可见；系统管理员（topic:view-all）全量
        boolean viewAll = permissionService.hasApiPerm(AuthContext.getUserId(), "topic:view-all");
        Set<String> participantNames = viewAll ? Set.of() : visibleParticipantNames();

        List<Map<String, Object>> rows = matched.stream()
                .filter(tp -> viewAll || participants(tp).stream().anyMatch(participantNames::contains))
                .map(tp -> toRow(tp, todoByTopic, canEdit))
                // 通用列筛选（表头漏斗，含待办数等派生字段）
                .filter(row -> RowFilters.matchRow(row, filters))
                .toList();
        long total = rows.size();
        int from = size > 0 ? (int) Math.min((current - 1L) * size, total) : 0;
        int to = size > 0 ? (int) Math.min(from + (long) size, total) : (int) total;
        return new PageResult<>(rows.subList(from, to), size > 0 ? current : 0, size, total);
    }

    /** 筛选候选值（FR-TOP-002）：状态读字典，部门/小组读组织树，负责人取现存专题 */
    public Map<String, Object> options() {
        requireView();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("statuses", dictNames(DICT_STATUS));
        // 组织树两级口径：level=2 为部门、level>=3 为小组
        // 2026-09-11 需求：负责部门/负责小组统一取组织管理的全部组织名称（不再按层级切分）
        result.put("depts", orgNames());
        result.put("teams", orgNames());
        result.put("owners", repositories.getTopic().findAll().stream()
                .map(Topic::getOwner)
                .filter(Objects::nonNull)
                .distinct()
                .sorted()
                .toList());
        return result;
    }

    /** 专题详情（FR-TOP-007）：统计概览（直属任务）+ 成员（角色字段推导）+ 操作日志 */
    public Map<String, Object> detail(String id) {
        requireView();
        Topic topic = requireTopic(id);
        syncTopicStatus();
        topic = requireTopic(id);
        requireTopicVisible(topic);

        List<com.bsp.admin.module.task.domain.Task> tasks = topicTasks(id);
        long total = tasks.size();
        long done = tasks.stream().filter(t -> "已完成".equals(t.getStatus())).count();
        long todo = total - done;
        long docCount = repositories.getDoc().findAll().stream()
                .filter(d -> id.equals(d.getTopicId()))
                .count();

        Map<String, Object> result = toRow(topic, todoCountByTopic(),
                permissionService.hasApiPerm(AuthContext.getUserId(), "topic:edit"));
        result.put("totalTaskCount", total);
        result.put("doneTaskCount", done);
        result.put("todoCount", todo);
        result.put("completionRate", total == 0 ? 0 : Math.round(done * 100.0 / total));
        result.put("docCount", docCount);
        result.put("members", members(topic));
        result.put("logs", repositories.getLog().findAll().stream()
                .filter(l -> "topic".equals(l.getModule()))
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
    public void create(TopicSaveRequest request) {
        requireEdit();
        validateDictValues(request);
        if (findByName(request.name().trim()) != null) {
            throw new BizException("已存在同名专题：" + request.name());
        }
        Topic topic = new Topic();
        topic.setId(nextId());
        applyFields(topic, request);
        if (empty(topic.getStatus())) {
            // 默认非活跃；有未完成任务时由 syncTopicStatus 自动转为活跃
            topic.setStatus("非活跃");
        }
        topic.setCreateTime(LocalDateTime.now());
        topic.setUpdateTime(LocalDateTime.now());
        repositories.getTopic().insert(topic);
        logTopic(topic, "新增", "—");
    }

    /** 编辑 */
    public void update(TopicSaveRequest request) {
        requireEdit();
        Topic topic = requireTopic(request.id());
        validateDictValues(request);
        Topic sameName = findByName(request.name().trim());
        if (sameName != null && !sameName.getId().equals(topic.getId())) {
            throw new BizException("已存在同名专题：" + request.name());
        }
        String old = brief(topic);
        applyFields(topic, request);
        topic.setUpdateTime(LocalDateTime.now());
        repositories.getTopic().updateById(topic);
        logTopic(topic, "编辑", old + " → " + brief(topic));
        // 编辑后口径可能变化，立即按待办重新流转
        syncTopicStatus();
    }

    /** 删除（FR-TOP-005）：级联删除直属任务与文档；返回级联数量供二次确认提示 */
    public Map<String, Object> delete(String id) {
        requireEdit();
        Topic topic = requireTopic(id);
        List<com.bsp.admin.module.task.domain.Task> tasks = repositories.getTask().findAll().stream()
                .filter(t -> id.equals(t.getTopicId()))
                .toList();
        long docCount = repositories.getDoc().findAll().stream()
                .filter(d -> id.equals(d.getTopicId()))
                .count();
        tasks.forEach(t -> repositories.getTask().deleteById(t.getId()));
        repositories.getDoc().findAll().stream()
                .filter(d -> id.equals(d.getTopicId()))
                .forEach(d -> repositories.getDoc().deleteById(d.getId()));
        repositories.getTopic().deleteById(id);
        logTopic(topic, "删除", "级联删除任务 " + tasks.size() + " 个、文档 " + docCount + " 个");
        return Map.of("tasks", tasks.size(), "docs", docCount);
    }

    // ==================== 状态自动流转（FR-TASK-014） ====================

    /** 待办>0 → 活跃；=0 → 非活跃（每次流转写日志；渲染前调用，与模块共用同一字典与规则） */
    public void syncTopicStatus() {
        Map<String, Long> todoByTopic = todoCountByTopic();
        for (Topic topic : repositories.getTopic().findAll()) {
            long todo = todoByTopic.getOrDefault(topic.getId(), 0L);
            String expected = todo > 0 ? "活跃" : "非活跃";
            if (!expected.equals(topic.getStatus())) {
                String old = topic.getStatus();
                topic.setStatus(expected);
                topic.setUpdateTime(LocalDateTime.now());
                repositories.getTopic().updateById(topic);
                logTopic(topic, "状态自动流转", (old == null ? "—" : old) + " → " + expected);
            }
        }
    }

    // ==================== CSV 导入导出（FR-TOP-006） ====================

    /** Excel 表头（导出/导入共用映射基准；无归属产品/模块分类两列） */
    public List<String> excelHeaders() {
        return List.of("专题名称", "负责部门", "负责小组",
                "专题负责人", "研发负责人", "测试负责人", "成本对象", "专题状态", "描述");
    }

    /** 导出行数据（xlsx 由控制器负责组装；列筛选与列表同口径） */
    public List<List<String>> excelRows(String kw, List<String> dept, List<String> team, List<String> owner,
                                      List<String> status, String scope,
                                      Map<String, RowFilters.Condition> filters) {
        requireView();
        syncTopicStatus();
        boolean mine = "mine".equalsIgnoreCase(scope);
        String myName = mine ? permissionService.currentUserName() : null;
        Map<String, Long> todoByTopic = todoCountByTopic();
        boolean canEdit = permissionService.hasApiPerm(AuthContext.getUserId(), "topic:edit");
        boolean viewAll = permissionService.hasApiPerm(AuthContext.getUserId(), "topic:view-all");
        Set<String> participantNames = viewAll ? Set.of() : visibleParticipantNames();
        return repositories.getTopic().findAll().stream()
                .filter(tp -> kw == null || kw.isBlank() || (tp.getName() != null && tp.getName().contains(kw)))
                .filter(tp -> matchAny(dept, tp.getDept()))
                .filter(tp -> matchAny(team, tp.getTeam()))
                .filter(tp -> matchAny(owner, tp.getOwner()))
                .filter(tp -> matchAny(status, tp.getStatus()))
                .filter(tp -> !mine || (myName != null && myName.equals(tp.getOwner())))
                .filter(tp -> viewAll || participants(tp).stream().anyMatch(participantNames::contains))
                .sorted(Comparator.comparing(Topic::getId))
                .map(tp -> toRow(tp, todoByTopic, canEdit))
                .filter(row -> RowFilters.matchRow(row, filters))
                .map(row -> List.of(
                        cell(row.get("name")), cell(row.get("dept")), cell(row.get("team")), cell(row.get("owner")),
                        row.get("rd") == null ? "" : String.join("、", (List<String>) row.get("rd")),
                        cell(row.get("test")), cell(row.get("cost")), cell(row.get("status")), cell(row.get("desc"))))
                .toList();
    }

    /** 导出单元格取值（null → 空串） */
    private String cell(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    /**
     * 导入（按表头映射批量创建）：
     * - 缺专题名称或与存量同名 → 整行跳过；
     * - 状态字典非法/停用值忽略（置空），其余字段原样入库；
     * - 返回 created / skipped。
     */
    public Map<String, Object> importRows(List<Map<String, String>> rows) {
        requireEdit();
        Set<String> statusNames = new HashSet<>(dictNames(DICT_STATUS));
        int created = 0;
        int skipped = 0;
        for (Map<String, String> row : rows) {
            String name = trimToNull(row.get("专题名称"));
            if (name == null || findByName(name) != null) {
                skipped++;
                continue;
            }
            Topic topic = new Topic();
            topic.setId(nextId());
            topic.setName(name);
            topic.setDept(trimToNull(row.get("负责部门")));
            topic.setTeam(trimToNull(row.get("负责小组")));
            topic.setOwner(trimToNull(row.get("专题负责人")));
            String rd = trimToNull(row.get("研发负责人"));
            topic.setRd(rd == null ? List.of() : splitNames(rd));
            topic.setTest(trimToNull(row.get("测试负责人")));
            topic.setCost(trimToNull(row.get("成本对象")));
            topic.setStatus(pickIfValid(trimToNull(row.get("专题状态")), statusNames));
            topic.setDesc(trimToNull(row.get("描述")));
            topic.setCreateTime(LocalDateTime.now());
            topic.setUpdateTime(LocalDateTime.now());
            repositories.getTopic().insert(topic);
            logTopic(topic, "导入", "CSV 批量导入");
            created++;
        }
        syncTopicStatus();
        return Map.of("created", created, "skipped", skipped);
    }

    // ==================== 内部工具 ====================

    private void requireView() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "topic:view")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无专题查看权限");
        }
    }

    private void requireEdit() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "topic:edit")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无专题编辑权限");
        }
    }

    private void validateDictValues(TopicSaveRequest request) {
        // 状态由任务待办自动流转，表单不再填写：仅在显式传入时才校验字典值
        if (!empty(request.status()) && !dictNames(DICT_STATUS).contains(request.status())) {
            throw new BizException("非法或已停用的专题状态：" + request.status());
        }
    }

    private void applyFields(Topic topic, TopicSaveRequest request) {
        topic.setName(request.name().trim());
        topic.setDept(request.dept());
        topic.setTeam(request.team());
        topic.setOwner(request.owner().trim());
        topic.setRd(request.rd() == null ? List.of() : request.rd().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList());
        topic.setTest(trimToNull(request.test()));
        topic.setCost(trimToNull(request.cost()));
        // 状态不由表单维护：未传则新建置「非活跃」、编辑保持原值（随后由 syncTopicStatus 按待办校正）
        if (!empty(request.status())) {
            topic.setStatus(request.status());
        }
        topic.setStakeholders(request.stakeholders() == null ? List.of() : request.stakeholders().stream()
                .filter(Objects::nonNull).map(String::trim).filter(s -> !s.isBlank()).toList());
        topic.setDesc(trimToNull(request.desc()));
    }

    /** 专题任务范围 = 直属任务（挂靠概念已移除） */
    private List<com.bsp.admin.module.task.domain.Task> topicTasks(String topicId) {
        return repositories.getTask().findAll().stream()
                .filter(t -> topicId.equals(t.getTopicId()))
                .toList();
    }

    /** 各专题待办数（状态 ≠ 已完成；仅统计直属任务） */
    private Map<String, Long> todoCountByTopic() {
        Map<String, Long> result = new HashMap<>();
        for (com.bsp.admin.module.task.domain.Task t : repositories.getTask().findAll()) {
            if ("已完成".equals(t.getStatus()) || t.getTopicId() == null) {
                continue;
            }
            result.merge(t.getTopicId(), 1L, Long::sum);
        }
        return result;
    }

    // ==================== 数据权限 ====================

    /** 专题参与者：专题负责人 / 研发负责人 / 测试负责人 */
    private List<String> participants(Topic topic) {
        List<String> names = new ArrayList<>();
        if (topic.getOwner() != null && !topic.getOwner().isBlank()) {
            names.add(topic.getOwner().trim());
        }
        if (topic.getRd() != null) {
            topic.getRd().stream().filter(n -> n != null && !n.isBlank()).map(String::trim).forEach(names::add);
        }
        if (topic.getTest() != null && !topic.getTest().isBlank()) {
            names.add(topic.getTest().trim());
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
    private void requireTopicVisible(Topic topic) {
        if (permissionService.hasApiPerm(AuthContext.getUserId(), "topic:view-all")) {
            return;
        }
        Set<String> visibleNames = visibleParticipantNames();
        if (participants(topic).stream().noneMatch(visibleNames::contains)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看该专题（仅参与者及其上级可见）");
        }
    }

    /** 专题成员（仅 专题负责人 / 研发负责人 / 测试负责人，与模块同口径） */
    private List<Map<String, Object>> members(Topic topic) {
        Map<String, Set<String>> roleByMember = new LinkedHashMap<>();
        addRole(roleByMember, topic.getOwner(), "专题负责人");
        if (topic.getRd() != null) {
            topic.getRd().forEach(name -> addRole(roleByMember, name, "研发负责人"));
        }
        addRole(roleByMember, topic.getTest(), "测试负责人");
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

    private Map<String, Object> toRow(Topic tp, Map<String, Long> todoByTopic, boolean canEdit) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", tp.getId());
        row.put("name", tp.getName());
        row.put("dept", tp.getDept());
        row.put("team", tp.getTeam());
        row.put("owner", tp.getOwner());
        row.put("rd", tp.getRd());
        row.put("test", tp.getTest());
        row.put("cost", tp.getCost());
        row.put("status", tp.getStatus());
        row.put("stakeholders", tp.getStakeholders());
        row.put("desc", tp.getDesc());
        row.put("todoCount", todoByTopic.getOrDefault(tp.getId(), 0L));
        row.put("canEdit", canEdit);
        row.put("createTime", tp.getCreateTime());
        row.put("updateTime", tp.getUpdateTime());
        return row;
    }

    /** 日志操作对象格式：名称(id)，与状态自动流转日志一致（详情页按后缀过滤） */
    private void logTopic(Topic topic, String action, String detail) {
        logService.addLogCurrentUser("topic", topic.getName() + "(" + topic.getId() + ")", action, detail);
    }

    private String brief(Topic topic) {
        return topic.getName() + " / " + nz(topic.getOwner()) + " / " + nz(topic.getStatus());
    }

    private Topic requireTopic(String id) {
        Topic topic = repositories.getTopic().findById(id);
        if (topic == null) {
            throw new BizException("专题不存在");
        }
        return topic;
    }

    private Topic findByName(String name) {
        return repositories.getTopic().findAll().stream()
                .filter(tp -> name.equals(tp.getName()))
                .findFirst()
                .orElse(null);
    }

    /** 专题主键：前缀 tp + 序号递增（06 文档 §3） */
    private String nextId() {
        AtomicLong max = new AtomicLong(0);
        repositories.getTopic().findAll().forEach(tp -> {
            String id = tp.getId();
            if (id != null && id.startsWith("tp")) {
                try {
                    max.set(Math.max(max.get(), Long.parseLong(id.substring(2))));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        return "tp" + (max.get() + 1);
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

    /** 多选筛选命中判定：候选为空 = 不筛选；否则值 ∈ 候选集合 */
    private boolean matchAny(List<String> candidates, String value) {
        if (candidates == null || candidates.isEmpty()) {
            return true;
        }
        return value != null && candidates.contains(value);
    }

    private boolean empty(String s) {
        return s == null || s.isBlank();
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
