import { AppRouteRecord } from '@/types/router'

/**
 * 专题管理（一级功能菜单：列表 + 详情隐藏路由；与模块管理同构，06 号需求）
 */
export const topicRoutes: AppRouteRecord[] = [
  {
    path: '/topic',
    name: 'TopicIndex',
    component: '/topic/index',
    meta: {
      title: 'menus.topic.title',
      icon: 'ri:bookmark-3-line',
      keepAlive: true
    }
  },
  {
    path: '/topic/detail/:id',
    name: 'TopicDetail',
    component: '/topic/detail',
    meta: {
      title: 'menus.topic.detail',
      icon: 'ri:bookmark-3-line',
      isHide: true,
      activePath: '/topic'
    }
  }
]
