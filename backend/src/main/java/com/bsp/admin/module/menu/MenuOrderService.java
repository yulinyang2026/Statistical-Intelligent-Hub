package com.bsp.admin.module.menu;

import com.bsp.admin.auth.AuthContext;
import com.bsp.admin.common.exception.BizException;
import com.bsp.admin.common.exception.ErrorCode;
import com.bsp.admin.module.log.LogService;
import com.bsp.admin.module.menu.domain.MenuOrder;
import com.bsp.admin.storage.Repositories;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 菜单顺序服务：读取/保存左侧功能树的自定义排序（全局共享，登录用户均可调整，不做权限点限制）
 */
@Service
@RequiredArgsConstructor
public class MenuOrderService {

    private static final String ORDER_ID = "menu-order";

    private final Repositories repositories;
    private final LogService logService;

    /** 读取菜单顺序（无记录时返回空列表 = 使用默认顺序） */
    public List<String> get() {
        requireLogin();
        MenuOrder order = repositories.getMenuOrder().findById(ORDER_ID);
        return order == null || order.getPaths() == null ? List.of() : order.getPaths();
    }

    /** 保存菜单顺序（整表覆盖；登录用户均可保存，全局共享） */
    public void save(List<String> paths) {
        requireLogin();
        List<String> cleaned = paths == null ? List.of() : paths.stream()
                .filter(p -> p != null && !p.isBlank())
                .distinct()
                .toList();
        MenuOrder order = repositories.getMenuOrder().findById(ORDER_ID);
        if (order == null) {
            order = new MenuOrder();
            order.setId(ORDER_ID);
            order.setPaths(cleaned);
            order.setUpdateTime(LocalDateTime.now());
            repositories.getMenuOrder().insert(order);
        } else {
            order.setPaths(cleaned);
            order.setUpdateTime(LocalDateTime.now());
            repositories.getMenuOrder().updateById(order);
        }
        logService.addLogCurrentUser("menu", "左侧菜单(" + ORDER_ID + ")", "拖动排序",
                "菜单顺序更新（" + cleaned.size() + " 项）");
    }

    /** 登录校验：菜单顺序对所有登录用户开放（用户需求：所有权限的人都可以拖动） */
    private void requireLogin() {
        if (AuthContext.getUserId() == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "请先登录");
        }
    }
}
