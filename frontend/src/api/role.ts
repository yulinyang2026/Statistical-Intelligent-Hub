import request from '@/utils/http'

/**
 * 角色与权限管理 API
 *
 * 角色直接分配给用户；授权 = 菜单/接口/数据三类权限勾选 + 数据范围设置。
 */

/** 分页查询角色 */
export function fetchRolePage(params: Api.Role.RoleSearchParams) {
  return request.get<Api.Role.RoleList>({
    url: '/api/system/role/page',
    params
  })
}

/** 启用角色下拉选项（用户表单分配角色） */
export function fetchRoleList() {
  return request.get<Api.Role.RoleOption[]>({
    url: '/api/system/role/list'
  })
}

/** 新增角色 */
export function fetchCreateRole(params: Api.Role.RoleSaveParams) {
  return request.post<null>({
    url: '/api/system/role',
    params
  })
}

/** 修改角色 */
export function fetchUpdateRole(params: Api.Role.RoleSaveParams) {
  return request.put<null>({
    url: '/api/system/role',
    params
  })
}

/** 删除角色（内置不可删） */
export function fetchDeleteRole(params: { id: number }) {
  return request.del<null>({
    url: `/api/system/role/${params.id}`
  })
}

/** 权限树（menu / api / data 三类） */
export function fetchPermissionTree() {
  return request.get<Api.Role.PermissionTreeNode[]>({
    url: '/api/system/role/permission-tree'
  })
}

/** 角色已分配权限（回显） */
export function fetchRolePermissions(params: { roleId: number }) {
  return request.get<Api.Role.RolePermissionResponse>({
    url: `/api/system/role/${params.roleId}/permissions`
  })
}

/** 角色授权（权限勾选 + 数据范围） */
export function fetchAssignRolePermissions(params: Api.Role.RoleAssignPermissionParams) {
  return request.put<null>({
    url: '/api/system/role/permissions',
    params
  })
}
