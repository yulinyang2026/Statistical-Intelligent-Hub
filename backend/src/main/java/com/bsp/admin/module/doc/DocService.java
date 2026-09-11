package com.bsp.admin.module.doc;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.auth.PermissionService;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.doc.domain.Doc;
import com.bsp.admin.module.doc.dto.DocSaveRequest;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.mgmt.domain.Module;
import com.bsp.admin.module.project.domain.Project;
import com.bsp.admin.module.topic.domain.Topic;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * 文档服务：全局标题检索、对象内文档 Tab 数据源、归属三选一校验、字数统计、只读权限标识。
 */
@Service
@RequiredArgsConstructor
public class DocService {

    private final Repositories repositories;
    private final PermissionService permissionService;
    private final LogService logService;

    /**
     * 文档列表（FR-DOC-001/005）：
     * - kw 标题模糊检索（跨对象）；moduleId/topicId/projectId 支持对象内 Tab 过滤；
     * - 摘要 = 正文前 40 字（去 Markdown 标记符号）；
     * - 分页默认 20 条/页，size=0 全量。
     */
    public PageResult<Map<String, Object>> list(int current, int size, String kw,
                                                 String projectId, String moduleId, String topicId,
                                                 Map<String, RowFilters.Condition> filters) {
        requireView();
        List<Map<String, Object>> rows = repositories.getDoc().findAll().stream()
                .filter(d -> kw == null || kw.isBlank()
                        || (d.getTitle() != null && d.getTitle().contains(kw)))
                .filter(d -> empty(projectId) || projectId.equals(d.getProjectId()))
                .filter(d -> empty(moduleId) || moduleId.equals(d.getModuleId()))
                .filter(d -> empty(topicId) || topicId.equals(d.getTopicId()))
                .sorted(Comparator.comparing(Doc::getUpdatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .map(this::toRow)
                // 通用列筛选（2026-09-10：所有字段均支持表头漏斗）
                .filter(row -> RowFilters.matchRow(row, filters))
                .toList();
        long total = rows.size();
        int from = size > 0 ? (int) Math.min((current - 1L) * size, total) : 0;
        int to = size > 0 ? (int) Math.min(from + (long) size, total) : (int) total;
        return new PageResult<>(rows.subList(from, to), size > 0 ? current : 0, size, total);
    }

    /** 文档详情（含 canEdit / canDelete，FR-DOC-006 只读预览判定） */
    public Map<String, Object> detail(String id) {
        requireView();
        Doc doc = requireDoc(id);
        Map<String, Object> row = toRow(doc);
        row.put("content", doc.getContent());
        return row;
    }

    /** 新增（归属三选一必填其一，FR-DOC-004） */
    public void create(DocSaveRequest request) {
        requireEdit();
        validateBelong(request);
        Doc doc = new Doc();
        doc.setId(nextId());
        applyFields(doc, request);
        doc.setUpdatedAt(LocalDateTime.now());
        repositories.getDoc().insert(doc);
        logDoc(doc, "新增", "—");
    }

    /** 编辑（归属可变更，仍三选一） */
    public void update(DocSaveRequest request) {
        requireEdit();
        Doc doc = requireDoc(request.id());
        validateBelong(request);
        String old = doc.getTitle();
        applyFields(doc, request);
        doc.setUpdatedAt(LocalDateTime.now());
        repositories.getDoc().updateById(doc);
        logDoc(doc, "编辑", old + " → " + doc.getTitle() + "（" + doc.getWordCount() + " 字）");
    }

    /** 删除（前端二次确认） */
    public void delete(String id) {
        Long uid = AuthContext.getUserId();
        if (!permissionService.hasApiPerm(uid, "doc:delete")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无文档删除权限");
        }
        Doc doc = requireDoc(id);
        repositories.getDoc().deleteById(id);
        logDoc(doc, "删除", "—");
    }

    // ==================== 内部工具 ====================

    private void requireView() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "doc:view")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无文档查看权限");
        }
    }

    private void requireEdit() {
        if (!permissionService.hasApiPerm(AuthContext.getUserId(), "doc:edit")) {
            throw new BizException(ErrorCode.FORBIDDEN, "无文档编辑权限");
        }
    }

    private void validateBelong(DocSaveRequest request) {
        int count = 0;
        if (!empty(request.projectId())) count++;
        if (!empty(request.moduleId())) count++;
        if (!empty(request.topicId())) count++;
        if (count == 0) {
            throw new BizException("文档必须归属项目、模块或专题之一");
        }
        if (count > 1) {
            throw new BizException("文档归属项目、模块、专题三选一");
        }
    }

    private void applyFields(Doc doc, DocSaveRequest request) {
        doc.setTitle(request.title().trim());
        doc.setProjectId(blankToNull(request.projectId()));
        doc.setModuleId(blankToNull(request.moduleId()));
        doc.setTopicId(blankToNull(request.topicId()));
        doc.setContent(request.content() == null ? "" : request.content());
        doc.setUpdatedBy(permissionService.currentUserName());
        doc.setWordCount(wordCount(doc.getContent()));
    }

    private Map<String, Object> toRow(Doc d) {
        Long uid = AuthContext.getUserId();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", d.getId());
        row.put("title", d.getTitle());
        row.put("projectId", d.getProjectId());
        row.put("moduleId", d.getModuleId());
        row.put("topicId", d.getTopicId());
        row.put("belongLabel", belongLabel(d));
        row.put("summary", summary(d.getContent()));
        row.put("updatedBy", d.getUpdatedBy());
        row.put("updatedAt", d.getUpdatedAt() == null ? null
                : d.getUpdatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        row.put("wordCount", d.getWordCount() == null ? 0 : d.getWordCount());
        row.put("canEdit", permissionService.hasApiPerm(uid, "doc:edit"));
        row.put("canDelete", permissionService.hasApiPerm(uid, "doc:delete"));
        return row;
    }

    /** 归属展示标签：项目/模块/专题 · 名称 */
    private String belongLabel(Doc d) {
        if (d.getProjectId() != null) {
            Map<String, String> names = repositories.getProject().findAll().stream()
                    .collect(Collectors.toMap(Project::getId, Project::getName, (a, b) -> a));
            return "项目·" + names.getOrDefault(d.getProjectId(), d.getProjectId());
        }
        if (d.getModuleId() != null) {
            Map<String, String> names = repositories.getModule().findAll().stream()
                    .collect(Collectors.toMap(Module::getId, Module::getName, (a, b) -> a));
            return "模块·" + names.getOrDefault(d.getModuleId(), d.getModuleId());
        }
        if (d.getTopicId() != null) {
            // 2026-09-11：专题正式实体落地，归属标签改读 Topic（原 BelongTopic 垫底数据源下线）
            Map<String, String> names = repositories.getTopic().findAll().stream()
                    .collect(Collectors.toMap(Topic::getId, Topic::getName, (a, b) -> a));
            return "专题·" + names.getOrDefault(d.getTopicId(), d.getTopicId());
        }
        return null;
    }

    /** 字数：正文字符数（不含空白） */
    private int wordCount(String content) {
        if (content == null) {
            return 0;
        }
        return (int) content.chars().filter(c -> !Character.isWhitespace(c)).count();
    }

    /** 摘要：去 Markdown 标记符号后正文前 40 字 */
    private String summary(String content) {
        if (content == null) {
            return "";
        }
        String plain = content.replaceAll("[#*`>\\-\\[\\]()|~\\s]+", "");
        return plain.length() > 40 ? plain.substring(0, 40) : plain;
    }

    private void logDoc(Doc doc, String action, String detail) {
        logService.addLogCurrentUser("doc", doc.getId() + " " + doc.getTitle(), action, detail);
    }

    private Doc requireDoc(String id) {
        Doc doc = repositories.getDoc().findById(id);
        if (doc == null) {
            throw new BizException("文档不存在");
        }
        return doc;
    }

    private String nextId() {
        AtomicLong max = new AtomicLong(0);
        repositories.getDoc().findAll().forEach(d -> {
            if (d.getId() != null && d.getId().startsWith("doc")) {
                try {
                    max.set(Math.max(max.get(), Long.parseLong(d.getId().substring(3))));
                } catch (NumberFormatException ignored) {
                }
            }
        });
        return "doc" + (max.get() + 1);
    }

    private boolean empty(String s) {
        return s == null || s.isBlank();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s;
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
