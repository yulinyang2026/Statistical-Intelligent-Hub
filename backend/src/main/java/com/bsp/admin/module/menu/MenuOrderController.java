package com.bsp.admin.module.menu;

import com.bsp.admin.common.response.BaseResponse;
import com.bsp.admin.module.menu.dto.MenuOrderSaveRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 左侧菜单顺序接口（2026-09-10 用户需求：功能树拖动排序，全局共享）
 *
 * <p>登录即可读写（不做权限点限制——所有权限的人都可以拖动）。</p>
 */
@RestController
@RequestMapping("/api/menu")
@RequiredArgsConstructor
public class MenuOrderController {

    private final MenuOrderService menuOrderService;

    /** 读取菜单顺序（空数组 = 使用默认顺序） */
    @GetMapping("/order")
    public BaseResponse<List<String>> get() {
        return BaseResponse.ok(menuOrderService.get());
    }

    /** 保存菜单顺序（整表覆盖，全局共享） */
    @PutMapping("/order")
    public BaseResponse<Void> save(@RequestBody MenuOrderSaveRequest request) {
        menuOrderService.save(request == null ? null : request.paths());
        return BaseResponse.ok();
    }
}
