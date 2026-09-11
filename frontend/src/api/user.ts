import request from '@/utils/http'

/**
 * 用户与账号管理 API
 *
 * 业务主键统一 user_id；一人一账号；主属部门有且仅有一个。
 */

/** 分页查询用户（姓名/工号/手机号模糊；状态/部门/角色精确） */
export function fetchUserPage(params: Api.User.UserSearchParams) {
  return request.get<Api.User.UserList>({
    url: '/api/system/user/page',
    params
  })
}

/** 用户详情（表单回显） */
export function fetchUserDetail(params: { id: number }) {
  return request.get<Api.User.UserDetail>({
    url: `/api/system/user/${params.id}`
  })
}

/** 新增用户 */
export function fetchCreateUser(params: Api.User.UserSaveParams) {
  return request.post<null>({
    url: '/api/system/user',
    params
  })
}

/** 修改用户（密码留空不变更） */
export function fetchUpdateUser(params: Api.User.UserSaveParams) {
  return request.put<null>({
    url: '/api/system/user',
    params
  })
}

/** 重置密码 */
export function fetchResetPassword(params: Api.User.ResetPasswordParams) {
  return request.put<null>({
    url: '/api/system/user/reset-password',
    params
  })
}

/** 启用/禁用（用户与账号状态联动） */
export function fetchChangeUserStatus(params: Api.User.StatusParams) {
  return request.put<null>({
    url: '/api/system/user/status',
    params
  })
}

/** 离职处理（禁用并解除部门归属，保留历史） */
export function fetchUserLeave(params: { userId: number }) {
  return request.post<null>({
    url: '/api/system/user/leave',
    params
  })
}

/** 删除用户（2026-09-11 用户需求：物理删除，连账号与部门/角色绑定一并移除） */
export function fetchDeleteUser(id: number) {
  return request.del<null>({
    url: `/api/system/user/${id}`
  })
}

/** 用户下拉选项（部门负责人 / 直属主管选择） */
export function fetchUserOptions(params?: { keyword?: string; excludeUserId?: number }) {
  return request.get<Api.User.UserOption[]>({
    url: '/api/system/user/options',
    params
  })
}

/**
 * 个人中心（2026-09-10 用户需求）：本人信息 / 修改密码 / 更换头像
 */

/** 本人基本信息（含部门与角色名称、头像） */
export function fetchUserProfile() {
  return request.get<Api.User.UserProfile>({
    url: '/api/user/profile'
  })
}

/** 修改密码（服务端校验原密码与新密码强度） */
export function fetchChangePassword(params: { oldPassword: string; newPassword: string }) {
  return request.put<null>({
    url: '/api/user/password',
    params
  })
}

/** 更换头像（data URL；传空字符串恢复默认头像） */
export function fetchUpdateAvatar(avatar: string) {
  return request.put<null>({
    url: '/api/user/avatar',
    params: { avatar }
  })
}

/** 读取本人界面设置（2026-09-11 用户需求：系统设置按用户存库） */
export function fetchUserSettings() {
  return request.get<Record<string, any>>({
    url: '/api/user/settings'
  })
}

/** 保存本人界面设置（整份覆盖） */
export function fetchSaveUserSettings(payload: Record<string, any>) {
  return request.put<null>({
    url: '/api/user/settings',
    params: payload
  })
}
