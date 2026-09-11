package com.bsp.admin.module.menu.dto;

import java.util.List;

/**
 * 菜单顺序保存入参（paths = 按显示顺序排列的菜单路径）
 */
public record MenuOrderSaveRequest(List<String> paths) {
}
