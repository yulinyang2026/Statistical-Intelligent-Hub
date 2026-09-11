import { AppRouteRecord } from '@/types/router'

/**
 * 文档中心（一级功能菜单，Markdown 文档全局检索）
 */
export const docRoutes: AppRouteRecord = {
  path: '/doc',
  name: 'DocIndex',
  component: '/doc/index',
  meta: {
    title: 'menus.doc.title',
    icon: 'ri:file-mark-line',
    keepAlive: true
  }
}
