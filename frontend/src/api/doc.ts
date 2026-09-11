import request from '@/utils/http'

/**
 * 文档中心 API（Markdown 文档，全局检索 + 对象内文档 Tab）
 */

/** 文档列表（kw 标题检索 + 对象内 Tab 过滤） */
export function fetchDocPage(params: Api.Doc.DocSearchParams) {
  return request.get<Api.Doc.DocList>({
    url: '/api/docs',
    params
  })
}

/** 文档详情（含正文与 canEdit / canDelete） */
export function fetchDocDetail(id: string) {
  return request.get<Api.Doc.DocDetail>({
    url: `/api/docs/${id}`
  })
}

/** 新增文档（归属三选一必填其一） */
export function fetchCreateDoc(params: Api.Doc.DocSaveParams) {
  return request.post<null>({
    url: '/api/docs',
    params
  })
}

/** 编辑文档 */
export function fetchUpdateDoc(id: string, params: Api.Doc.DocSaveParams) {
  return request.put<null>({
    url: `/api/docs/${id}`,
    params
  })
}

/** 删除文档（前端二次确认） */
export function fetchDeleteDoc(id: string) {
  return request.del<null>({
    url: `/api/docs/${id}`
  })
}

/** 批量删除（2026-09-11 用户需求：按钮仅超级管理员可见；服务端按权限点 batch:delete 判权） */
export function fetchBatchDeleteDocs(ids: string[]) {
  return request.post<{ deleted: number; failed: string[] }>({
    url: '/api/docs/batch-delete',
    params: { ids }
  })
}
