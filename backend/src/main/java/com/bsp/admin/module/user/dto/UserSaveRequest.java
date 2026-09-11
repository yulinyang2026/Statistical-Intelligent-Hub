package com.bsp.admin.module.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 用户新增/修改入参（id 为空 = 新增）
 *
 * <p>同时承载：人员档案 + 登录账号 + 部门归属（主属/兼职）+ 角色分配。</p>
 */
public record UserSaveRequest(

        Long id,

        @NotBlank(message = "姓名不能为空")
        @Size(max = 50, message = "姓名不能超过 50 个字符")
        String name,

        @NotBlank(message = "工号不能为空")
        @Size(max = 50, message = "工号不能超过 50 个字符")
        String employeeNo,

        /** 手机号：**2026-09-11 起改为选填**（新增用户表单不再填写），存量数据与列表展示不受影响 */
        @Size(max = 20, message = "手机号不能超过 20 个字符")
        String mobile,

        @Size(max = 100, message = "邮箱不能超过 100 个字符")
        String email,

        /** 直属主管（可空，禁止成环） */
        Long managerUserId,

        /** 0 停用 / 1 启用 */
        Integer status,

        /** 归属部门集合（含主属与兼职），须包含 primaryOrgId */
        List<Long> orgIds,

        /** 主属部门（有且仅有 1 个） */
        Long primaryOrgId,

        /** 登录账号（一人一账号，username 唯一） */
        String username,

        /** 口令：新增必填；修改留空表示不变更 */
        String password,

        /** 角色集合（直接挂人，无岗位层） */
        List<Long> roleIds,

        /**
         * 组织管理页新增用户时的「创建属性」（2026-09-11 用户需求）：
         * true = 该机构的管理者（同时写入该机构的负责人）；false = 下级成员（直属主管默认为该机构负责人）。
         * null = 不处理（普通入口不传）。
         */
        Boolean asOrgLeader
) {
}
