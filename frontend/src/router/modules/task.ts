import { AppRouteRecord } from '@/types/router'

/**
 * 任务管理（一级功能菜单，列表 / 看板双视图，全员可见）
 */
export const taskRoutes: AppRouteRecord = {
  path: '/task',
  name: 'TaskIndex',
  component: '/task/index',
  meta: {
    title: 'menus.task.title',
    icon: 'ri:task-line',
    keepAlive: true
  }
}
