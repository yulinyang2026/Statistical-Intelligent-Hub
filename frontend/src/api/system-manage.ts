import request from '@/utils/http'
import { AppRouteRecord } from '@/types/router'

// 获取菜单列表（后端模式使用，被路由引擎 MenuProcessor 引用）
export function fetchGetMenuList() {
  return request.get<AppRouteRecord[]>({
    url: '/api/v3/system/menus'
  })
}

/**
 * 系统级操作日志（2026-09-11 用户需求）：记录所有人操作轨迹，仅系统管理员（log:view）可查
 */

/** 操作日志分页（筛选：操作人/模块/动作/关键字/时间区间 + 表头漏斗 filters） */
export function fetchLogPage(params: Api.Log.LogSearchParams) {
  return request.get<Api.Common.PaginatedResponse<Api.Log.LogItem>>({
    url: '/api/system/log/page',
    params
  })
}
