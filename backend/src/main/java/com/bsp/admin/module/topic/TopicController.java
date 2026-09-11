package com.bsp.admin.module.topic;

import com.bsp.admin.common.filter.RowFilters;
import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.common.response.PageResult;
import com.bsp.admin.module.topic.dto.TopicSaveRequest;
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
 * 专题管理接口（路径复数 /api/topics，同时兼容单数 /api/topic，勿改回单数）
 */
@RestController
@RequestMapping({"/api/topics", "/api/topic"})
@RequiredArgsConstructor
public class TopicController {

    private final TopicService topicService;

    /** 专题列表（名称模糊 + 部门/小组/负责人/状态多选 + scope=mine；size=0 全量） */
    @GetMapping
    public BaseResponse<PageResult<Map<String, Object>>> list(
            @RequestParam(defaultValue = "1") int current,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String kw,
            @RequestParam(required = false) List<String> dept,
            @RequestParam(required = false) List<String> team,
            @RequestParam(required = false) List<String> owner,
            @RequestParam(required = false) List<String> status,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String filters) {
        return BaseResponse.ok(topicService.list(current, size, kw, dept, team, owner, status,
                scope, RowFilters.parse(filters)));
    }

    /** 筛选候选值（状态字典 + 部门/小组组织树 + 现有负责人） */
    @GetMapping("/options")
    public BaseResponse<Map<String, Object>> options() {
        return BaseResponse.ok(topicService.options());
    }

    /** 专题详情（统计概览 / 成员 / 操作日志） */
    @GetMapping("/{id}")
    public BaseResponse<Map<String, Object>> detail(@PathVariable String id) {
        return BaseResponse.ok(topicService.detail(id));
    }

    /** 新增专题 */
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody TopicSaveRequest request) {
        topicService.create(request);
        return BaseResponse.ok();
    }

    /** 编辑专题 */
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody TopicSaveRequest request) {
        topicService.update(request);
        return BaseResponse.ok();
    }

    /** 删除专题（级联删除直属任务与文档；返回级联数量） */
    @DeleteMapping("/{id}")
    public BaseResponse<Map<String, Object>> delete(@PathVariable String id) {
        return BaseResponse.ok(topicService.delete(id));
    }

    /**
     * 批量删除（2026-09-11 用户需求）：前端「批量删除」按钮**仅超级管理员可见**，
     * 服务端按权限点 `batch:delete`（只绑 R_SUPER）二次校验；逐条走单条删除的级联逻辑。
     */
    @PostMapping("/batch-delete")
    public BaseResponse<Map<String, Object>> batchDelete(@RequestBody Map<String, List<String>> body) {
        return BaseResponse.ok(topicService.batchDelete(body == null ? null : body.get("ids")));
    }
}
