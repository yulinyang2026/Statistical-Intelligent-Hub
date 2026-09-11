import request from '@/utils/http'

/**
 * 左侧菜单顺序 API（2026-09-10 用户需求：功能树支持拖动排序，顺序全局共享）
 */

/** 读取菜单顺序（空数组 = 默认顺序） */
export function fetchMenuOrder() {
  return request.get<string[]>({
    url: '/api/menu/order'
  })
}

/** 保存菜单顺序（整表覆盖，所有登录用户均可保存） */
export function saveMenuOrder(paths: string[]) {
  return request.put<null>({
    url: '/api/menu/order',
    params: { paths }
  })
}
