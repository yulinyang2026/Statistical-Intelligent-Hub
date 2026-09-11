package com.bsp.admin.module.task;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.task.dto.TaskListItem;
import com.bsp.admin.module.task.dto.TaskPatchRequest;
import com.bsp.admin.module.task.dto.TaskPatchResult;
import com.bsp.admin.module.task.dto.TaskSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * 任务管理接口（两级树 / 筛选补链 / 单字段写回 / 级联删除）
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    /** 任务列表（树形，两级；顶层分页；归属/状态/优先级/执行人/小组支持多选） */
    @GetMapping
    public BaseResponse<PageResult<TaskListItem>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kw,
            @RequestParam(required = false) List<String> projectId,
            @RequestParam(required = false) List<String> moduleId,
            @RequestParam(required = false) List<String> topicId,
            @RequestParam(required = false) String subsystemId,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) List<String> priority,
            @RequestParam(required = false) List<String> assignee,
            @RequestParam(required = false) List<String> group,
            @RequestParam(required = false) String deadline,
            @RequestParam(required = false) String filters) {
        return BaseResponse.ok(taskService.list(current, size, kw, projectId, moduleId, topicId,
                subsystemId, status, priority, assignee, group, deadline,
                com.bsp.admin.common.filter.RowFilters.parse(filters)));
    }

    /** 筛选候选值（归属/截止月份/状态/优先级/执行人/小组/子系统） */
    @GetMapping("/options")
    public BaseResponse<Map<String, Object>> options() {
        return BaseResponse.ok(taskService.options());
    }

    /** 新增任务（可无归属；子任务继承父任务归属与子系统） */
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody TaskSaveRequest request) {
        taskService.create(request);
        return BaseResponse.ok();
    }

    /** 修改任务（含子任务环检测、层级限制校验） */
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody TaskSaveRequest request) {
        taskService.update(request);
        return BaseResponse.ok();
    }

    /** 单字段更新（看板拖拽 / 工时双击编辑）：field = status/priority/assignee/group/deadline/planDate/hours；值未变化返回 changed=false */
    @PatchMapping("/{id}")
    public BaseResponse<TaskPatchResult> patch(@PathVariable String id, @Valid @RequestBody TaskPatchRequest request) {
        return BaseResponse.ok(taskService.patch(id, request));
    }

    /** 删除任务（级联删除子任务，返回 cascaded 数量） */
    @DeleteMapping("/{id}")
    public BaseResponse<Map<String, Object>> delete(@PathVariable String id) {
        return BaseResponse.ok(Map.of("cascaded", taskService.delete(id)));
    }

    /**
     * 批量删除（2026-09-11 用户需求）：前端「批量删除」按钮**仅超级管理员可见**，
     * 服务端按权限点 `batch:delete`（只绑 R_SUPER）二次校验；逐条走单条删除的级联逻辑。
     */
    @PostMapping("/batch-delete")
    public BaseResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, List<String>> body) {
        return BaseResponse.ok(taskService.batchDelete(body == null ? null : body.get("ids")));
    }
}
