import { AppRouteRecord } from '@/types/router'

/**
 * 模块管理（一级功能菜单：列表 + 详情隐藏路由）
 */
export const moduleRoutes: AppRouteRecord[] = [
  {
    path: '/module',
    name: 'ModuleIndex',
    component: '/module/index',
    meta: {
      title: 'menus.module.title',
      icon: 'ri:apps-2-line',
      keepAlive: true
    }
  },
  {
    path: '/module/detail/:id',
    name: 'ModuleDetail',
    component: '/module/detail',
    meta: {
      title: 'menus.module.detail',
      icon: 'ri:apps-2-line',
      isHide: true,
      activePath: '/module'
    }
  }
]
