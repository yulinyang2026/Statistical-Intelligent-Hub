import { fetchSaveUserSettings, fetchUserSettings } from '@/api/user'
import { useSettingStore } from '@/store/modules/setting'
import { useTableStore } from '@/store/modules/table'
import { useUserStore } from '@/store/modules/user'

/**
 * 界面设置按用户存库（2026-09-11 用户需求）。
 *
 * <p>此前设置只写浏览器 localStorage，换浏览器/换电脑就丢，也无法「每人一套风格」。
 * 现在登录后从后端读回本人设置并应用，之后任何设置变更都防抖回写服务端（按 userId 存）。</p>
 *
 * <p>用法：在应用根组件（`App.vue`）调用一次即可，不依赖具体页面。</p>
 */

/** 需要按用户同步的设置项（setting store 中与「外观 / 偏好」相关的键；refresh 等运行时标记不同步） */
const SETTING_KEYS = [
  // 菜单
  'menuType',
  'menuOpenWidth',
  'menuOpen',
  'dualMenuShowText',
  // 主题
  'systemThemeType',
  'systemThemeMode',
  'menuThemeType',
  'systemThemeColor',
  // 顶栏 / 界面显示
  'showMenuButton',
  'showFastEnter',
  'showRefreshButton',
  'showCrumbs',
  'showWorkTab',
  'showLanguage',
  'showNprogress',
  'showSettingGuide',
  'watermarkVisible',
  'autoClose',
  'uniqueOpened',
  'colorWeak',
  // 样式
  'boxBorderMode',
  'pageTransition',
  'tabStyle',
  'customRadius',
  'containerWidth'
]

/** 表格显示开关（table store；2026-09-11 用户需求：一并按用户同步） */
const TABLE_KEYS = ['isZebra', 'isBorder', 'isHeaderBackground']

/**
 * 表格开关的服务端缺省值：用户从未保存过该项时按此初始化。
 * 这三项是后加入同步的，老用户的存量设置行里没有它们——若只「有则覆盖」，
 * 本地 localStorage 中的旧值会一直生效、默认值改动永远看不到，故缺省时显式落默认。
 * 注：须与 `store/modules/table.ts` 的初始值保持一致。
 */
const TABLE_DEFAULTS: Record<string, boolean> = {
  isZebra: true,
  isBorder: false,
  isHeaderBackground: true
}

/** 回写防抖时长（ms） */
const SAVE_DEBOUNCE = 800

export function useUserSettings() {
  const settingStore = useSettingStore()
  const tableStore = useTableStore()
  const userStore = useUserStore()

  /** 正在应用服务端设置：此期间的变更不再回写，避免来回覆盖 */
  let applying = false
  let timer: ReturnType<typeof setTimeout> | null = null

  /** 取当前设置中需要同步的部分（两个 store 的偏好键合并为一份 payload） */
  const pick = (): Record<string, any> => {
    const result: Record<string, any> = {}
    const settingState = settingStore.$state as unknown as Record<string, any>
    SETTING_KEYS.forEach((key) => {
      if (key in settingState) {
        result[key] = settingState[key]
      }
    })
    const tableState = tableStore.$state as unknown as Record<string, any>
    TABLE_KEYS.forEach((key) => {
      if (key in tableState) {
        result[key] = tableState[key]
      }
    })
    return result
  }

  /** 登录后拉取本人设置并应用（失败时保持本地设置，不打断使用） */
  const load = async () => {
    if (!userStore.accessToken) {
      return
    }
    try {
      const saved = (await fetchUserSettings()) ?? {}
      applying = true
      const settingState = settingStore.$state as unknown as Record<string, any>
      SETTING_KEYS.forEach((key) => {
        if (key in saved) {
          settingState[key] = saved[key]
        }
      })
      // 表格开关：存量设置行没有这三项，缺省时落到代码默认（见 TABLE_DEFAULTS 注释）
      const tableState = tableStore.$state as unknown as Record<string, any>
      TABLE_KEYS.forEach((key) => {
        tableState[key] = key in saved ? saved[key] : TABLE_DEFAULTS[key]
      })
      await nextTick()
    } catch {
      // 忽略：读取失败时保持本地设置
    } finally {
      applying = false
    }
  }

  /** 变更后防抖回写 */
  const scheduleSave = () => {
    if (applying || !userStore.accessToken) {
      return
    }
    if (timer) {
      clearTimeout(timer)
    }
    timer = setTimeout(() => {
      fetchSaveUserSettings(pick()).catch(() => {
        // 忽略：回写失败不影响使用（下次变更会重试）
      })
    }, SAVE_DEBOUNCE)
  }

  // 任一设置项（setting store 或 table store）变化即回写
  watch(pick, scheduleSave, { deep: true })
  // 应用启动（已有 token）与登录成功后各拉取一次本人设置
  watch(
    () => userStore.accessToken,
    (token) => {
      if (token) {
        load()
      }
    },
    { immediate: true }
  )
}
