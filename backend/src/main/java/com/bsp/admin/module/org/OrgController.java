package com.bsp.admin.module.org;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.module.org.dto.OrgSaveRequest;
import com.bsp.admin.module.org.dto.OrgTransferRequest;
import com.bsp.admin.module.org.dto.OrgTreeNode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 组织机构接口
 */
@RestController
@RequestMapping("/api/system/org")
@RequiredArgsConstructor
public class OrgController {

    private final OrgService orgService;

    /** 部门树（树形展示，含负责人、人员数） */
    @GetMapping("/tree")
    public BaseResponse<List<OrgTreeNode>> tree() {
        return BaseResponse.ok(orgService.tree());
    }

    /** 新增部门 */
    @PostMapping
    public BaseResponse<Void> create(@Valid @RequestBody OrgSaveRequest request) {
        orgService.save(request);
        return BaseResponse.ok();
    }

    /** 修改 / 移动部门（id 非空，parentId 变化即移动） */
    @PutMapping
    public BaseResponse<Void> update(@Valid @RequestBody OrgSaveRequest request) {
        orgService.save(request);
        return BaseResponse.ok();
    }

    /** 删除部门（无子部门且无人员才可删） */
    @DeleteMapping("/{id}")
    public BaseResponse<Void> delete(@PathVariable Long id) {
        orgService.delete(id);
        return BaseResponse.ok();
    }

    /** 批量转移部门人员到目标部门 */
    @PostMapping("/transfer")
    public BaseResponse<Void> transfer(@Valid @RequestBody OrgTransferRequest request) {
        orgService.transfer(request);
        return BaseResponse.ok();
    }
}
