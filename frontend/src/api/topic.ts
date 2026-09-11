import request from '@/utils/http'

/**
 * 专题管理 API（06-专题管理；列表筛选 / 详情 / CRUD / 级联删除 / Excel 导入导出）
 * 接口与模块管理同构，差异仅在路径（/api/topics）与字段（无归属产品 / 模块分类）
 */

/** 专题列表（筛选变更即查询；size=0 全量） */
export function fetchTopicPage(params: Api.Topic.TopicSearchParams) {
  return request.get<Api.Topic.TopicList>({
    url: '/api/topics',
    params
  })
}

/** 筛选候选值（状态字典 + 部门/小组组织树 + 现有负责人） */
export function fetchTopicOptions() {
  return request.get<Api.Topic.TopicOptions>({
    url: '/api/topics/options'
  })
}

/** 专题详情（统计概览 / 成员 / 操作日志） */
export function fetchTopicDetail(id: string) {
  return request.get<Api.Topic.TopicDetail>({
    url: `/api/topics/${id}`
  })
}

/** 新增专题 */
export function fetchCreateTopic(params: Api.Topic.TopicSaveParams) {
  return request.post<null>({
    url: '/api/topics',
    params
  })
}

/** 编辑专题 */
export function fetchUpdateTopic(id: string, params: Api.Topic.TopicSaveParams) {
  return request.put<null>({
    url: `/api/topics/${id}`,
    params
  })
}

/** 删除专题（级联删除直属任务与文档） */
export function fetchDeleteTopic(id: string) {
  return request.del<Api.Topic.TopicDeleteResult>({
    url: `/api/topics/${id}`
  })
}

/** 导出 Excel(.xlsx) 下载地址（浏览器直开；token 走 ?token= 参数） */
export function topicExportUrl(params: Record<string, any>, token: string) {
  const query = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && `${value}` !== '') {
      query.append(key, `${value}`)
    }
  })
  query.append('token', token)
  // 开发走 Vite 代理、生产走同域反代。
  // 注意：开发环境 VITE_API_URL = '/'，必须先去掉结尾斜杠再拼接，
  // 否则会得到 '//api/export/topics...'——浏览器按**协议相对 URL** 解析（主机名变成 api）导致导出必然失败（2026-09-11 修复）
  const base = (import.meta.env.VITE_API_URL ?? '').replace(/\/+$/, '')
  return `${base}/api/export/topics?${query.toString()}`
}

/** 导入 Excel(.xlsx)（multipart；按表头映射，非法字典值忽略） */
export function fetchImportTopic(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post<Api.Topic.TopicImportResult>({
    url: '/api/import/topics',
    data: formData
  })
}

/** 批量删除（2026-09-11 用户需求：按钮仅超级管理员可见；服务端按权限点 batch:delete 判权） */
export function fetchBatchDeleteTopics(ids: string[]) {
  return request.post<{ deleted: number; failed: string[] }>({
    url: '/api/topics/batch-delete',
    params: { ids }
  })
}
