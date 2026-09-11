/**
 * 菜单状态管理模块
 *
 * 提供菜单数据和动态路由的状态管理
 *
 * ## 主要功能
 *
 * - 菜单列表存储和管理
 * - 首页路径配置
 * - 动态路由注册和移除
 * - 路由移除函数管理
 * - 菜单宽度配置
 *
 * ## 使用场景
 *
 * - 动态菜单加载和渲染
 * - 路由权限控制
 * - 首页路径动态设置
 * - 登出时清理动态路由
 *
 * ## 工作流程
 *
 * 1. 获取菜单数据（前端/后端模式）
 * 2. 设置菜单列表和首页路径
 * 3. 注册动态路由并保存移除函数
 * 4. 登出时调用移除函数清理路由
 *
 * @module store/modules/menu
 * @author Statistical Intelligent Hub Team
 */
import { defineStore } from 'pinia'
import { ref } from 'vue'
import { AppRouteRecord } from '@/types/router'
import { getFirstMenuPath } from '@/utils'
import { HOME_PAGE_PATH } from '@/router'
import { fetchMenuOrder, saveMenuOrder } from '@/api/menu'

/**
 * 菜单状态管理
 * 管理应用的菜单列表、首页路径、菜单宽度和动态路由移除函数
 *
 * 2026-09-10 用户需求补充：左侧功能树支持拖动排序，顺序存后端全局共享（menuOrder）。
 */
export const useMenuStore = defineStore('menuStore', () => {
  /** 首页路径 */
  const homePath = ref(HOME_PAGE_PATH)
  /** 菜单列表 */
  const menuList = ref<AppRouteRecord[]>([])
  /** 菜单宽度 */
  const menuWidth = ref('')
  /** 存储路由移除函数的数组 */
  const removeRouteFns = ref<(() => void)[]>([])

  /** 左侧菜单自定义顺序（菜单 path 数组，DFS 顺序；空 = 默认顺序） */
  const menuOrder = ref<string[]>([])

  /** 按自定义顺序排序菜单树（未记录的路径按原相对顺序排在末尾） */
  const sortByMenuOrder = (list: AppRouteRecord[]): AppRouteRecord[] => {
    const rank = (path?: string) => {
      const index = path ? menuOrder.value.indexOf(path) : -1
      return index === -1 ? Number.MAX_SAFE_INTEGER : index
    }
    return list
      .map((item, index) => ({ item, index }))
      .sort((a, b) => rank(a.item.path) - rank(b.item.path) || a.index - b.index)
      .map(({ item }) => {
        const children = item.children
        return children?.length ? { ...item, children: sortByMenuOrder(children) } : item
      })
  }

  /**
   * 设置菜单列表
   * @param list 菜单路由记录数组
   */
  const setMenuList = (list: AppRouteRecord[]) => {
    menuList.value = menuOrder.value.length ? sortByMenuOrder(list) : list
    setHomePath(HOME_PAGE_PATH || getFirstMenuPath(list))
  }

  /**
   * 拉取已保存的菜单顺序并应用到当前菜单（登录后调用一次）
   */
  const loadMenuOrder = async (): Promise<void> => {
    try {
      const paths = await fetchMenuOrder()
      if (Array.isArray(paths) && paths.length) {
        menuOrder.value = paths
        if (menuList.value.length) {
          menuList.value = sortByMenuOrder(menuList.value)
        }
      }
    } catch {
      // 忽略：拉取失败时保持默认顺序，不阻断界面
    }
  }

  /**
   * 保存当前菜单顺序（左侧菜单拖动结束后调用；隐藏菜单不参与持久化）
   */
  const persistMenuOrder = async (): Promise<void> => {
    const paths: string[] = []
    const walk = (list: AppRouteRecord[]) => {
      for (const item of list) {
        if (item.meta?.isHide) {
          continue
        }
        if (item.path) {
          paths.push(item.path)
        }
        if (item.children?.length) {
          walk(item.children)
        }
      }
    }
    walk(menuList.value)
    menuOrder.value = paths
    try {
      await saveMenuOrder(paths)
    } catch {
      // 忽略：保存失败不阻断界面（下次拖动会再次尝试）
    }
  }

  /**
   * 获取首页路径
   * @returns 首页路径字符串
   */
  const getHomePath = () => homePath.value

  /**
   * 设置主页路径
   * @param path 主页路径
   */
  const setHomePath = (path: string) => {
    homePath.value = path
  }

  /**
   * 添加路由移除函数
   * @param fns 要添加的路由移除函数数组
   */
  const addRemoveRouteFns = (fns: (() => void)[]) => {
    removeRouteFns.value.push(...fns)
  }

  /**
   * 移除所有动态路由
   * 执行所有存储的路由移除函数并清空数组
   */
  const removeAllDynamicRoutes = () => {
    removeRouteFns.value.forEach((fn) => fn())
    removeRouteFns.value = []
  }

  /**
   * 清空路由移除函数数组
   */
  const clearRemoveRouteFns = () => {
    removeRouteFns.value = []
  }

  return {
    menuList,
    menuWidth,
    removeRouteFns,
    menuOrder,
    setMenuList,
    getHomePath,
    setHomePath,
    addRemoveRouteFns,
    removeAllDynamicRoutes,
    clearRemoveRouteFns,
    loadMenuOrder,
    persistMenuOrder,
    sortByMenuOrder
  }
})
