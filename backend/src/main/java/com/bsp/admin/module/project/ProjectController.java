package com.bsp.admin.module.project;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.project.dto.ProjectSaveRequest;
import com.bsp.admin.module.project.dto.SubsystemSaveRequest;
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
 * 项目管理接口（路径复数 /api/projects，同时兼容单数 /api/project，与模块接口同约定）
 */
@RestController
@RequestMapping({"/api/projects", "/api/project"})
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectService projectService;

    /** 项目列表（树形：项目 → 子系统；名称/成本对象模糊 + 状态/产品/负责人/级别精确 + scope=mine） */
    @GetMapping
    public BaseResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kw,
            @RequestParam(required = false) List<String> product,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> owner,
            @RequestParam(required = false) List<String> level,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String filters) {
        return BaseResponse.ok(projectService.list(current, size, kw, product, status, owner, level, scope,
                com.bsp.admin.common.filter.RowFilters.parse(filters)));
    }

    /** 筛选候选值（产品/状态字典 + 级别 + 部门/小组组织树 + 挂靠对象） */
    @GetMapping("/options")
    public BaseResponse<Map<String, Object>> options() {
        return BaseResponse.ok(projectService.options());
    }

    /** 项目详情（统计概览 / 子系统 / 成员 / 操作日志） */
    @GetMapping("/{id}")
    public BaseResponse<Map<String, Object>> detail(@PathVariable String id) {
        return BaseResponse.ok(projectService.detail(id));
    }

    /** 新增项目 */
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody ProjectSaveRequest request) {
        projectService.create(request);
        return BaseResponse.ok();
    }

    /** 编辑项目 */
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody ProjectSaveRequest request) {
        projectService.update(request);
        return BaseResponse.ok();
    }

    /** 删除项目（级联删除子系统、任务与文档；返回各类数量） */
    @DeleteMapping("/{id}")
    public BaseResponse<Map<String, Object>> delete(@PathVariable String id) {
        return BaseResponse.ok(projectService.delete(id));
    }

    /** 新增子系统（FR-PROJ-013：项目详情「子系统」Tab 与列表页「新增子系统」共用） */
    @PostMapping("/{id}/subsystems")
    public BaseResponse<Void> createSubsystem(@PathVariable String id,
                                              @Valid @RequestBody SubsystemSaveRequest request) {
        projectService.createSubsystem(id, request);
        return BaseResponse.ok();
    }

    /**
     * 批量删除（2026-09-11 用户需求）：前端「批量删除」按钮**仅超级管理员可见**，
     * 服务端按权限点 `batch:delete`（只绑 R_SUPER）二次校验；逐条走单条删除的级联逻辑。
     */
    @PostMapping("/batch-delete")
    public BaseResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, List<String>> body) {
        return BaseResponse.ok(projectService.batchDelete(body == null ? null : body.get("ids")));
    }
}
