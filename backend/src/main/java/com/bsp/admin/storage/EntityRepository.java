package com.bsp.admin.storage;

import java.util.List;
import java.util.Map;

/**
 * 实体仓储抽象（二期切换达梦时新增实现，Service 层零改动）
 *
 * @param <T> 实体类型
 */
public interface EntityRepository<T> {

    /** 按主键查询 */
    T findById(Long id);

    /** 全量查询（返回副本，调用方可任意排序/过滤） */
    List<T> findAll();

    /**
     * 条件查询 + 分页：等值匹配（复杂条件由 Service 内存过滤，见技术设计文档 5.3）
     *
     * @param condition 字段名 -> 期望值（等值）
     * @param current   页码（从 1 起）
     * @param size      每页条数
     */
    List<T> findByCondition(Map<String, Object> condition, int current, int size);

    /** 插入（分配自增 ID 并落盘） */
    T insert(T entity);

    /** 按主键全量更新 */
    T updateById(T entity);

    /** 按主键删除 */
    void deleteById(Long id);
}
