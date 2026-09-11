import { AppRouteRecord } from '@/types/router'

export const demoRoutes: AppRouteRecord = {
  path: '/demo',
  name: 'Demo',
  component: '/index/index',
  meta: {
    title: 'menus.demo.title',
    icon: 'ri:flask-line'
  },
  children: [
    {
      path: 'user',
      name: 'DemoUser',
      component: '/demo/user/index',
      meta: {
        title: 'menus.demo.user',
        icon: 'ri:user-settings-line',
        keepAlive: true
      }
    }
  ]
}
