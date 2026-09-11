package com.bsp.admin.module.task.dto;

/**
 * 单字段更新（看板拖拽 / 工时双击编辑）结果。
 *
 * @param changed 值是否发生变化；false 表示请求值与现值一致，服务端未写库、未写操作日志
 */
public record TaskPatchResult(boolean changed) {
}
