package com.bsp.admin.module.doc;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.doc.dto.DocSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 文档中心接口（路径复数 /api/docs）
 */
@RestController
@RequestMapping("/api/docs")
@RequiredArgsConstructor
public class DocController {

    private final DocService docService;

    /** 文档列表（kw 标题检索 + 对象内 Tab 过滤；默认 20 条/页） */
    @GetMapping
    public BaseResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kw,
            @RequestParam(required = false) String projectId,
            @RequestParam(required = false) String moduleId,
            @RequestParam(required = false) String topicId,
            @RequestParam(required = false) String filters) {
        return BaseResponse.ok(docService.list(current, size, kw, projectId, moduleId, topicId,
                com.bsp.admin.common.filter.RowFilters.parse(filters)));
    }

    /** 文档详情（含 content 与 canEdit / canDelete） */
    @GetMapping("/{id}")
    public BaseResponse<Map<String, Object>> detail(@PathVariable String id) {
        return BaseResponse.ok(docService.detail(id));
    }

    /** 新增文档（归属三选一必填其一） */
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody DocSaveRequest request) {
        docService.create(request);
        return BaseResponse.ok();
    }

    /** 编辑文档 */
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody DocSaveRequest request) {
        docService.update(request);
        return BaseResponse.ok();
    }

    /** 删除文档（前端二次确认） */
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable String id) {
        docService.delete(id);
        return BaseResponse.ok();
    }

    /**
     * 批量删除（2026-09-11 用户需求）：前端「批量删除」按钮**仅超级管理员可见**，
     * 服务端按权限点 `batch:delete`（只绑 R_SUPER）二次校验；逐条走单条删除的级联逻辑。
     */
    @PostMapping("/batch-delete")
    public BaseResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, List<String>> body) {
        return BaseResponse.ok(docService.batchDelete(body == null ? null : body.get("ids")));
    }
}
