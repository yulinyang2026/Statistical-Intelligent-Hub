import { AppRouteRecord } from '@/types/router'

/**
 * 系统配置路由：部门管理 / 用户管理 / 角色管理
 * （2026-09-09 由原 org / user / role 三个一级分组合并）
 */
export const systemRoutes: AppRouteRecord = {
  path: '/system',
  name: 'System',
  component: '/index/index',
  meta: {
    title: 'menus.systemConfig.title',
    // 2026-09-11 用户需求：系统配置分组及其子菜单按「菜单权限」控制，
    // 未授权的角色（如普通用户）看不到该分组；权限点在角色管理中授权
    perm: 'menu:system',
    icon: 'ri:settings-3-line'
  },
  children: [
    {
      path: 'org',
      name: 'OrgIndex',
      component: '/org/index',
      meta: {
        title: 'menus.org.index',
        perm: 'menu:system:org',
        icon: 'ri:node-tree',
        keepAlive: true
      }
    },
    // 2026-09-11 用户需求：用户管理菜单取消——用户维护并入「组织管理」
    // （机构树 + 所选机构下的用户表格）；views/user/index.vue 保留作参考模板，不再注册路由
    {
      path: 'role',
      name: 'RoleIndex',
      component: '/role/index',
      meta: {
        title: 'menus.role.index',
        perm: 'menu:system:role',
        icon: 'ri:shield-user-line',
        keepAlive: true
      }
    },
    {
      path: 'dict',
      name: 'DictIndex',
      component: '/dict/index',
      meta: {
        title: 'menus.dict.index',
        perm: 'menu:system:dict',
        icon: 'ri:price-tag-3-line',
        keepAlive: true
      }
    },
    {
      // 2026-09-11 操作日志（系统级全量轨迹，仅管理员可见；后端按 log:view 判权）
      path: 'log',
      name: 'LogIndex',
      component: '/log/index',
      meta: {
        title: 'menus.log.title',
        perm: 'menu:system:log',
        icon: 'ri:file-list-3-line',
        keepAlive: true
      }
    },
    {
      // 2026-09-10 个人中心（顶栏头像下拉入口；不进入侧边栏，仅路由可达）
      path: 'user-center',
      name: 'UserCenter',
      component: '/user-center/index',
      meta: {
        title: 'menus.userCenter.title',
        icon: 'ri:user-3-line',
        isHide: true,
        activePath: '/system/user-center'
      }
    }
  ]
}
