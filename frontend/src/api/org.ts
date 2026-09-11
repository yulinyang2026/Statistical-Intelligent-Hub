import request from '@/utils/http'

/**
 * 组织机构管理 API
 *
 * 部门树 parent_id + path 描述层级；删除前校验无子部门且无人员。
 */

/** 部门树（树形展示，含负责人、在职人员数） */
export function fetchOrgTree() {
  return request.get<Api.Org.OrgTreeNode[]>({
    url: '/api/system/org/tree'
  })
}

/** 新增部门 */
export function fetchCreateOrg(params: Api.Org.OrgSaveParams) {
  return request.post<null>({
    url: '/api/system/org',
    params
  })
}

/** 修改 / 移动部门（id 非空，parentId 变化即移动） */
export function fetchUpdateOrg(params: Api.Org.OrgSaveParams) {
  return request.put<null>({
    url: '/api/system/org',
    params
  })
}

/** 删除部门（无子部门且无人员才可删） */
export function fetchDeleteOrg(params: { id: number }) {
  return request.del<null>({
    url: `/api/system/org/${params.id}`
  })
}

/** 批量转移部门人员到目标部门 */
export function fetchTransferOrgUsers(params: Api.Org.OrgTransferParams) {
  return request.post<null>({
    url: '/api/system/org/transfer',
    params
  })
}
