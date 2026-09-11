import { AppRouteRecord } from '@/types/router'
import { systemRoutes } from './system'
import { taskRoutes } from './task'
import { moduleRoutes } from './module'
import { topicRoutes } from './topic'
import { projectRoutes } from './project'
import { docRoutes } from './doc'

/**
 * 导出所有模块化路由
 *
 * 2026-09-10 按需求移除框架演示菜单：演示页面（demo）、结果页面（result）、
 * 异常页面（exception）、官方文档/精简版本/v2.6.1（help）不再注册进侧边栏，
 * 路由模块文件保留未删（见 01-概述与公共约定.md FR-UI-001）。
 * 2026-09-10 新增模块管理（列表 + 详情隐藏路由）与文档中心（05 / 10 号需求）。
 * 2026-09-10 新增项目管理（列表树形 + 详情隐藏路由，07 号需求）。
 * 2026-09-11 新增专题管理（列表 + 详情隐藏路由，06 号需求；与模块管理同构）。
 */
export const routeModules: AppRouteRecord[] = [
  taskRoutes,
  ...moduleRoutes,
  ...topicRoutes,
  ...projectRoutes,
  docRoutes,
  systemRoutes
]
