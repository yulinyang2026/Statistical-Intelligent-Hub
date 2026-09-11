package com.bsp.admin.common.response;

import java.util.List;

/**
 * 分页结果（字段名 records / current / size / total 与前端分页契约逐字对齐）
 *
 * @param records 当前页记录
 * @param current 页码（从 1 起）
 * @param size    每页条数
 * @param total   总条数
 */
public record PageResult<T>(List<T> records, long current, long size, long total) {

    /** 内存分页：对已过滤的完整列表切片 */
    public static <T> PageResult<T> of(List<T> all, long current, long size) {
        long total = all.size();
        int from = (int) Math.min((current - 1) * size, total);
        int to = (int) Math.min(from + size, total);
        return new PageResult<>(all.subList(from, to), current, size, total);
    }
}
