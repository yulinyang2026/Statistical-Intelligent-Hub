package com.bsp.admin.module.project;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.module.project.dto.SubsystemSaveRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 子系统接口（路径复数 /api/subsystems，兼容单数）：清单（支持 projectId，返回任务统计）、编辑、删除
 */
@RestController
@RequestMapping({"/api/subsystems", "/api/subsystem"})
@RequiredArgsConstructor
public class SubsystemController {

    private final ProjectService projectService;

    /** 子系统清单（?projectId= 过滤；含任务总数/待办数/完成率） */
    @GetMapping
    public BaseResponse<List<Map<String, Object>>> list(@RequestParam(required = false) String projectId) {
        return BaseResponse.ok(projectService.subsystemList(projectId));
    }

    /** 编辑子系统 */
    @PutMapping("/{id}")
    public BaseResponse<Void> update(@PathVariable String id, @Valid @RequestBody SubsystemSaveRequest request) {
        projectService.updateSubsystem(request);
        return BaseResponse.ok();
    }

    /** 删除子系统（任务不删除，仅解除关联；返回受影响任务数） */
    @DeleteMapping("/{id}")
    public BaseResponse<Map<String, Object>> delete(@PathVariable String id) {
        return BaseResponse.ok(projectService.deleteSubsystem(id));
    }
}
