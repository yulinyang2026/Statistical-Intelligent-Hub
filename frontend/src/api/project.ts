import request from '@/utils/http'

/**
 * 项目管理 API（列表树形（项目 → 子系统）/ 详情 / CRUD 级联删除 / 子系统维护 / Excel 导入导出）
 */

/** 项目列表（树形：项目 → 子系统；筛选变更即查询；size=0 全量） */
export function fetchProjectPage(params: Api.Project.ProjectSearchParams) {
  return request.get<Api.Project.ProjectList>({
    url: '/api/projects',
    params
  })
}

/** 筛选候选值（产品/状态字典 + 级别 + 部门/小组组织树 + 挂靠对象候选） */
export function fetchProjectOptions() {
  return request.get<Api.Project.ProjectOptions>({
    url: '/api/projects/options'
  })
}

/** 项目详情（统计概览 / 子系统 / 成员 / 操作日志） */
export function fetchProjectDetail(id: string) {
  return request.get<Api.Project.ProjectDetail>({
    url: `/api/projects/${id}`
  })
}

/** 新增项目 */
export function fetchCreateProject(params: Api.Project.ProjectSaveParams) {
  return request.post<null>({
    url: '/api/projects',
    params
  })
}

/** 编辑项目 */
export function fetchUpdateProject(id: string, params: Api.Project.ProjectSaveParams) {
  return request.put<null>({
    url: `/api/projects/${id}`,
    params
  })
}

/** 删除项目（级联删除子系统、任务与文档） */
export function fetchDeleteProject(id: string) {
  return request.del<Api.Project.ProjectDeleteResult>({
    url: `/api/projects/${id}`
  })
}

/** 子系统清单（按项目过滤，含任务统计；不传 projectId 返回全部） */
export function fetchSubsystemList(projectId?: string) {
  return request.get<Api.Project.SubsystemItem[]>({
    url: '/api/subsystems',
    params: projectId ? { projectId } : {}
  })
}

/** 新增子系统（POST /api/projects/:id/subsystems） */
export function fetchCreateSubsystem(projectId: string, params: Api.Project.SubsystemSaveParams) {
  return request.post<null>({
    url: `/api/projects/${projectId}/subsystems`,
    params
  })
}

/** 编辑子系统 */
export function fetchUpdateSubsystem(id: string, params: Api.Project.SubsystemSaveParams) {
  return request.put<null>({
    url: `/api/subsystems/${id}`,
    params
  })
}

/** 删除子系统（任务不删除，仅解除关联；返回受影响任务数） */
export function fetchDeleteSubsystem(id: string) {
  return request.del<Api.Project.SubsystemDeleteResult>({
    url: `/api/subsystems/${id}`
  })
}

/** 导出 Excel(.xlsx) 下载地址（浏览器直开；token 走 ?token= 参数） */
export function projectExportUrl(params: Record<string, any>, token: string) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}` !== '') {
      query.append(key, `${value}`)
    }
  })
  query.append('token', token)
  // 开发走 Vite 代理、生产走同域反代。
  // 注意：开发环境 VITE_API_URL = '/'，必须先去掉结尾斜杠再拼接，
  // 否则会得到 '//api/export/projects...'——浏览器按**协议相对 URL** 解析（主机名变成 api）导致导出必然失败（2026-09-11 修复）
  const base = (import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '')
  return `${base}/api/export/projects?${query.toString()}`
}

/** 导入 Excel(.xlsx)（multipart；按表头映射，非法字典值忽略） */
export function fetchImportProject(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<Api.Project.ProjectImportResult>({
    url: '/api/import/projects',
    data: formData
  })
}

/** 批量删除（2026-09-11 用户需求：按钮仅超级管理员可见；服务端按权限点 batch:delete 判权） */
export function fetchBatchDeleteProjects(ids: string[]) {
  return request.post<{ deleted: number; failed: string[] }>({
    url: '/api/projects/batch-delete',
    params: { ids }
  })
}
