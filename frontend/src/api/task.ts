import request from '@/utils/http'

/**
 * 任务管理 API（两级树 / 筛选补链 / 单字段写回 / 级联删除）
 */

/** 任务列表（树形，顶层分页） */
export function fetchTaskPage(params: Api.Task.TaskSearchParams) {
  return request.get<Api.Task.TaskList>({
    url: '/api/tasks',
    params
  })
}

/** 筛选候选值（归属/截止月份/状态/优先级/执行人/小组/子系统） */
export function fetchTaskOptions() {
  return request.get<Api.Task.TaskOptions>({
    url: '/api/tasks/options'
  })
}

/** 新增任务（可无归属；子任务继承父任务归属与子系统） */
export function fetchCreateTask(params: Api.Task.TaskSaveParams) {
  return request.post<null>({
    url: '/api/tasks',
    params
  })
}

/** 修改任务（含子任务环检测） */
export function fetchUpdateTask(id: string, params: Api.Task.TaskSaveParams) {
  return request.put<null>({
    url: `/api/tasks/${id}`,
    params
  })
}

/** 单字段更新（看板拖拽 / 工时双击编辑）：field = status/priority/assignee/group/deadline/planDate/hours；值未变化返回 changed=false */
export function fetchPatchTask(id: string, params: { field: string; value: string | number }) {
  return request.request<Api.Task.TaskPatchResult>({
    url: `/api/tasks/${id}`,
    method: 'PATCH',
    // 注意：必须走 data（http 封装仅对 POST/PUT 自动转 body，PATCH 传 params 会留在 query string）
    data: params
  })
}

/** 删除任务（级联删除子任务，返回 cascaded 数量） */
export function fetchDeleteTask(id: string) {
  return request.del<{ cascaded: number }>({
    url: `/api/tasks/${id}`
  })
}

/** 批量删除（2026-09-11 用户需求：按钮仅超级管理员可见；服务端按权限点 batch:delete 判权） */
export function fetchBatchDeleteTasks(ids: string[]) {
  return request.post<{ deleted: number; failed: string[] }>({
    url: '/api/tasks/batch-delete',
    params: { ids }
  })
}
