import { AppRouteRecord } from '@/types/router'

/**
 * 项目管理（一级功能菜单：列表（树形含子系统） + 详情隐藏路由）
 */
export const projectRoutes: AppRouteRecord[] = [
  {
    path: '/project',
    name: 'ProjectIndex',
    component: '/project/index',
    meta: {
      title: 'menus.project.title',
      icon: 'ri:folders-line',
      keepAlive: true
    }
  },
  {
    path: '/project/detail/:id',
    name: 'ProjectDetail',
    component: '/project/detail',
    meta: {
      title: 'menus.project.detail',
      icon: 'ri:folders-line',
      isHide: true,
      activePath: '/project'
    }
  }
]
