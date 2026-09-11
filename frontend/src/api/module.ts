import request from '@/utils/http'

/**
 * 模块管理 API（列表筛选 / 详情 / CRUD / 级联删除 / Excel 导入导出）
 */

/** 模块列表（筛选变更即查询；size=0 全量） */
export function fetchModulePage(params: Api.Module.ModuleSearchParams) {
  return request.get<Api.Module.ModuleList>({
    url: '/api/modules',
    params
  })
}

/** 筛选候选值（产品/分类/状态字典 + 部门/小组组织树 + 现有负责人） */
export function fetchModuleOptions() {
  return request.get<Api.Module.ModuleOptions>({
    url: '/api/modules/options'
  })
}

/** 模块详情（统计概览 / 挂靠项目 / 成员 / 操作日志） */
export function fetchModuleDetail(id: string) {
  return request.get<Api.Module.ModuleDetail>({
    url: `/api/modules/${id}`
  })
}

/** 新增模块 */
export function fetchCreateModule(params: Api.Module.ModuleSaveParams) {
  return request.post<null>({
    url: '/api/modules',
    params
  })
}

/** 编辑模块 */
export function fetchUpdateModule(id: string, params: Api.Module.ModuleSaveParams) {
  return request.put<null>({
    url: `/api/modules/${id}`,
    params
  })
}

/** 删除模块（级联删除直属任务与文档、清空挂靠引用） */
export function fetchDeleteModule(id: string) {
  return request.del<Api.Module.ModuleDeleteResult>({
    url: `/api/modules/${id}`
  })
}

/** 导出 Excel(.xlsx) 下载地址（浏览器直开；token 走 ?token= 参数） */
export function moduleExportUrl(params: Record<string, any>, token: string) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}` !== '') {
      query.append(key, `${value}`)
    }
  })
  query.append('token', token)
  // 开发走 Vite 代理、生产走同域反代。
  // 注意：开发环境 VITE_API_URL = '/'，必须先去掉结尾斜杠再拼接，
  // 否则会得到 '//api/export/modules...'——浏览器按**协议相对 URL** 解析（主机名变成 api）导致导出必然失败（2026-09-11 修复）
  const base = (import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '')
  return `${base}/api/export/modules?${query.toString()}`
}

/** 导入 Excel(.xlsx)（multipart；按表头映射，非法字典值忽略） */
export function fetchImportModule(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<Api.Module.ModuleImportResult>({
    url: '/api/import/modules',
    data: formData
  })
}

/** 批量删除（2026-09-11 用户需求：按钮仅超级管理员可见；服务端按权限点 batch:delete 判权） */
export function fetchBatchDeleteModules(ids: string[]) {
  return request.post<{ deleted: number; failed: string[] }>({
    url: '/api/modules/batch-delete',
    params: { ids }
  })
}
