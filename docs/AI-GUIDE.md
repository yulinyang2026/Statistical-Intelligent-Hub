# AI开发规则（必须严格遵守）

> 本文件的所有路径均已对照当前工程源码核验（非通用模板）。
> 当前工程状态：`frontend/src/views/` 下已清空全部演示业务页，仅保留 `auth / exception / index / outside / result`。
> 因此「参考模板」以**核心组件源码 + 本文第 4 节骨架**为准。

---

## 1. 需求沟通

- 每次执行时需要以提问的方式明确真实需求（范围、目标页面、数据来源、验收标准），禁止在不确认的情况下直接动手写码。
- 需求涉及修改框架层文件（见第 5 节）时，必须先提出替代方案并等待确认。
- 需求不明确时优先提问，而不是"先写一版给你看"。
- **需求与改动必须落需求文档并保留修改记录（强制）**：用户每次提出的需求/改动意向，先登记 `docs/需求规格/16-待确认与非需求项.md` 第 2 节「对话需求登记」；定稿后归位到对应模块文件（涉及字段/枚举先改 `02-数据模型.md`）并在条目内留痕（提出时间/来源/结论）；每次改动在 `docs/需求规格/99-变更记录与文档约定.md` 版本记录追加一行并递增版本号。**未同步文档视为任务未完成。**

---

## 2. 样式规则

- 所有**颜色、圆角、阴影、间距**必须使用工程定义好的变量 / Tailwind 原子类，**绝对禁止硬编码色值 / 像素值**（禁止 `#xxx`、`rgb()`、`border-radius: 8px`、`box-shadow: 0 2px 8px rgba(...)`、`margin: 16px` 这类写法）。
- 全局字体、动画、过渡效果全部沿用现有配置，**不要自定义新动画**（不新增 `@keyframes`、不新增 transition 曲线、不改 `duration`）。
- 新页面的 padding、margin 和现有页面保持一致，**不要自己调整**；沿用 Tailwind 原子类即可（`p-4`、`mt-4`、`gap-3`），容器统一使用 `.page-content`。
- 需要视觉调整时，只能改全局变量，不要在页面里写局部样式。

**颜色 / 圆角变量来源（唯一真源）：`frontend/src/assets/styles/core/tailwind.css`**

| 类别 | 可用变量 |
|---|---|
| 主题色 | `--art-primary`、`--theme-color`、`--main-color`、`--el-color-primary` |
| 语义色 | `--art-success`、`--art-warning`、`--art-danger`、`--art-error`、`--art-info`、`--art-secondary` |
| 灰阶 | `--art-gray-100` ~ `--art-gray-900`（暗色模式自动反转） |
| 背景 / 容器 | `--default-bg-color`、`--default-box-color`、`--art-hover-color`、`--art-active-color` |
| 边框 | `--art-card-border`、`--default-border`、`--default-border-dashed` |
| 圆角 / 尺寸 | `--custom-radius`、`--el-component-custom-height` |
| Element 变量 | `--el-border-radius-base`、`--el-color-primary-light-9` 等（`frontend/src/assets/styles/core/el-ui.scss`） |

**容器 / 卡片类（直接用，不要自己写样式）：**

- `.page-content` —— 页面根容器（自带边框 + 主题适配，`frontend/src/assets/styles/core/app.scss`）
- `.art-card` / `.art-card-sm` / `.art-card-xs` —— 卡片容器（跟随设置面板的边框/阴影模式自动切换）
- `.art-table-card`、`.art-badge`、`.art-text-badge` —— 见 `app.scss`

---

## 3. 组件规则

- **表格**优先使用 `useTable` hooks + `ArtTable` + `ArtTableHeader`（一体化支持分页、搜索、列显隐、缓存、5 种刷新策略），**不要用原生 `ElTable` 从零写**。
- **表单**使用 `ArtForm`，**搜索区**使用 `ArtSearchBar`，**不要用原生 `ElForm` 从零堆**。
- **图标**优先使用 `frontend/src/assets/images/` 下的现有资源（`@imgs` 别名）与 Iconify（`ArtSvgIcon` / Iconify 图标名如 `ri:pie-chart-line`），**不要引入新的图标库**。
- **弹窗、抽屉、消息提示**全部沿用 Element Plus 全局样式配置（`ElDialog` / `ElDrawer` / `ElMessage` / `ElMessageBox`），**不要复写样式**。
- 行内操作按钮统一用 `ArtButtonTable`（`type`: `add | edit | delete | more | view`）。
- 二次开发优先级：`components/core` 已有 → 不要自己写第二套。

**导入规则（已实测，照抄避免踩坑）：**

| 名称 | 是否需要手写 import |
|---|---|
| 模板中的 `ElXxx` 组件、`Art*` 组件、`v-auth` / `v-ripple` 指令 | ❌ 不需要（自动注册） |
| `ref` / `computed` / `reactive` / `useTemplateRef` 等 Vue API | ❌ 不需要（auto-import） |
| `ElMessage` | ❌ 不需要（`ElMessage` 已在 auto-imports.d.ts） |
| `ElMessageBox` | ✅ 需要：`import { ElMessageBox } from 'element-plus'` |
| `useI18n` | ✅ 需要：`import { useI18n } from 'vue-i18n'` |
| `useTable` / `useTableColumns` | ✅ 需要：`import { useTable } from '@/hooks'` |
| `request` | ✅ 需要：`import request from '@/utils/http'` |

**可直接复用的核心清单：**

| 能力 | 名称 | 路径 |
|---|---|---|
| 表格 | `ArtTable` / `ArtTableHeader` | `frontend/src/components/core/tables/` |
| 表单 / 搜索栏 | `ArtForm` / `ArtSearchBar` | `frontend/src/components/core/forms/` |
| 表格数据 | `useTable` / `useTableColumns` | `frontend/src/hooks/core/` |
| 图表 | `ArtLineChart` 等 9 个 | `frontend/src/components/core/charts/` |
| 卡片 | `ArtStatsCard` 等 8 个 | `frontend/src/components/core/cards/` |
| 异常 / 结果页 | `ArtException` / `ArtResultPage` | `frontend/src/components/core/views/` |
| 请求 | `request`（`get/post/put/del`） | `frontend/src/utils/http/index.ts` |
| 校验规则 | 手机号 / 身份证 / 银行卡 / 密码强度等 | `frontend/src/utils/form/validator.ts` |
| 按钮权限 | `v-auth` | `frontend/src/directives/core/auth.ts` |

---

## 4. 参考模板

> ⚠️ 工程已无保留的业务示例页（原 `system/user`、`examples/*` 等已删除）。
> **API 真源**请以下列组件源码为准；**页面结构**请直接复制第 4.3 / 4.4 的骨架（已按真实 API 编写）。

### 4.1 权威来源（组件源码 = 规范）

| 想写的页面 | 先读源码 |
|---|---|
| 表格列配置 `ColumnOption` | `frontend/src/types/component/index.ts` |
| 表格 props / 插槽 / 分页事件 | `frontend/src/components/core/tables/art-table/index.vue` |
| 表单项 `FormItem`（`key/label/type/props/options/span/hidden/render/slots`） | `frontend/src/components/core/forms/art-form/index.vue` |
| 搜索项 `SearchFormItem`（同上） | `frontend/src/components/core/forms/art-search-bar/index.vue` |
| 表格数据 hooks 配置与返回值 | `frontend/src/hooks/core/useTable.ts` |
| 现有页面外壳写法 | `frontend/src/views/result/success/index.vue`、`frontend/src/views/exception/404/index.vue` |
| 菜单/路由写法 | `frontend/src/router/modules/result.ts`、`frontend/src/router/modules/exception.ts` |
| 接口定义写法 | `frontend/src/api/system-manage.ts`、`frontend/src/types/api/api.d.ts` |

### 4.2 关键约定（照抄，不要自创）

- 页面根目录 class：**`.page-content`**
- `<script setup lang="ts">` 必须有 `defineOptions({ name: 'Xxx' })`，name 与路由 `name` 保持一致
- 表格：`ArtTable` 的插槽名 = 列的 `prop`，且该列必须标注 `useSlot: true`
- 分页事件：`@pagination:size-change`、`@pagination:current-change`
- 接口泛型：`request.get<Api.Common.PaginatedResponse<XxxItem>>({ url, params })`
- 文案统一走 `t('xxx')`，新业务 key 加进 `frontend/src/locales/langs/zh.json` / `en.json`（当前 `common` 节点只有 `tips / cancel / confirm / logOutTips`）

### 4.3 列表页骨架

```vue
<!-- frontend/src/views/<module>/index.vue -->
<template>
  <div class="page-content">
    <ArtSearchBar
      v-model="searchForm"
      :items="searchItems"
      @search="handleSearch"
      @reset="handleReset"
    />

    <ArtTable
      class="mt-4"
      :loading="loading"
      :data="data"
      :columns="columns"
      :pagination="pagination"
      @pagination:size-change="handleSizeChange"
      @pagination:current-change="handleCurrentChange"
    >
      <template #status="{ row }">
        <ElTag :type="row.status === 1 ? 'success' : 'info'">
          {{ row.status === 1 ? t('status.enable') : t('status.disable') }}
        </ElTag>
      </template>
      <template #operation="{ row }">
        <ArtButtonTable type="edit" @click="openForm(row)" />
        <ArtButtonTable type="delete" @click="handleDelete(row)" />
      </template>
    </ArtTable>
  </div>
</template>

<script setup lang="ts">
  import { fetchXxxPage, fetchDeleteXxx } from '@/api/xxx'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable } from '@/hooks'
  import type { ColumnOption } from '@/types'

  defineOptions({ name: 'XxxList' })

  const { t } = useI18n()

  /** 搜索表单（与 useTable 的 searchParams 解耦，避免分页字段被清空） */
  const searchForm = ref<Record<string, any>>({})

  const searchItems = computed(() => [
    {
      key: 'name',
      label: t('xxx.name'),
      type: 'input',
      props: { placeholder: t('xxx.namePlaceholder'), clearable: true }
    },
    {
      key: 'status',
      label: t('xxx.status'),
      type: 'select',
      props: { placeholder: t('xxx.statusPlaceholder'), clearable: true, options: statusOptions.value }
    }
  ])

  const statusOptions = ref([
    { label: t('status.enable'), value: 1 },
    { label: t('status.disable'), value: 2 }
  ])

  const columns = computed<ColumnOption[]>(() => [
    { type: 'index', label: t('common.index'), width: 60 },
    { prop: 'name', label: t('xxx.name'), minWidth: 160 },
    { prop: 'status', label: t('xxx.status'), width: 100, useSlot: true },
    { prop: 'operation', label: t('common.operation'), width: 120, fixed: 'right', useSlot: true }
  ])

  const {
    data,
    loading,
    pagination,
    replaceSearchParams,
    resetSearchParams,
    handleSizeChange,
    handleCurrentChange,
    fetchData,
    refreshUpdate,
    refreshRemove
  } = useTable({
    core: { apiFn: fetchXxxPage }
  })

  /** 查询：替换搜索条件并回到第一页 */
  const handleSearch = () => {
    replaceSearchParams({ ...searchForm.value })
    fetchData()
  }

  /** 重置：清空条件（useTable 内部会自动重新请求） */
  const handleReset = () => {
    searchForm.value = {}
    resetSearchParams()
  }

  const openForm = (row?: Record<string, any>) => {
    // 见 4.4：打开 ElDialog + ArtForm
    console.log(row)
  }

  const handleDelete = async (row: { id: number }) => {
    await ElMessageBox.confirm(t('common.deleteTips'), t('common.tips'), { type: 'warning' })
    await fetchDeleteXxx({ id: row.id })
    ElMessage.success(t('common.deleteSuccess'))
    refreshRemove()
  }
</script>
```

> 刷新语义（务必用对）：`refreshCreate()` 新增后、`refreshUpdate()` 编辑后、`refreshRemove()` 删除后、`refreshData()` 手动刷新、`refreshSoft()` 定时轻量刷新。

### 4.4 表单弹窗骨架

```vue
<!-- frontend/src/views/<module>/modules/xxx-dialog.vue -->
<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="600px"
    :close-on-click-modal="false"
    append-to-body
    @closed="handleClosed"
  >
    <ArtForm ref="formRef" v-model="formData" :items="formItems" />
    <template #footer>
      <ElButton @click="visible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton type="primary" v-ripple @click="handleSubmit">
        {{ t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import { fetchSaveXxx } from '@/api/xxx'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'XxxDialog' })

  const emit = defineEmits<{ success: [] }>()

  const { t } = useI18n()

  const visible = defineModel<boolean>('visible', { default: false })
  // ArtForm 是全局注册组件（模板可直接用），但脚本里取不到它的类型，
  // 因此用结构化类型声明 ref，避免 import 组件导致多余依赖
  const formRef = useTemplateRef<{
    validate: () => Promise<boolean>
    reset: () => void
    getOutput: () => Record<string, any>
  }>('formRef')

  const isEdit = ref(false)
  const formData = ref<Record<string, any>>({})

  const dialogTitle = computed(() => (isEdit.value ? t('xxx.edit') : t('xxx.add')))

  const formItems = computed(() => [
    {
      key: 'name',
      label: t('xxx.name'),
      type: 'input',
      span: 12,
      props: { placeholder: t('xxx.namePlaceholder'), maxlength: 50 }
    },
    {
      key: 'status',
      label: t('xxx.status'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('xxx.statusPlaceholder'),
        options: [
          { label: t('status.enable'), value: 1 },
          { label: t('status.disable'), value: 2 }
        ]
      }
    }
  ])

  const open = (row?: Record<string, any>) => {
    isEdit.value = !!row
    formData.value = row ? { ...row } : {}
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = {}
  }

  const handleSubmit = async () => {
    // ArtForm 对外暴露：ref / validate / reset / getOutput
    await formRef.value?.validate()
    await fetchSaveXxx(formData.value)
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
```

### 4.5 新增一个业务模块的标准动作

1. `frontend/src/views/<module>/index.vue`（列表）+ `frontend/src/views/<module>/modules/xxx-dialog.vue`（表单）
2. `frontend/src/api/<module>.ts` 定义接口，类型写进 `frontend/src/types/api/api.d.ts`
3. `frontend/src/router/modules/<module>.ts` 写菜单（`component: '/index/index'` 为一级菜单布局，子项 `component: '/<module>/index'`）
4. 在 `frontend/src/router/modules/index.ts` 的 import 与 `routeModules` 数组中登记
5. 文案加进 `frontend/src/locales/langs/zh.json` 与 `en.json`
6. 按第 6.1 节验证构建与类型（`vite build` + `vue-tsc` 只看自身文件是否 0 错误）

---

## 5. 禁止事项

- **绝对不要修改**（注意：本工程的真实路径与通用模板不同）：
  - `frontend/src/assets/`（含 `frontend/src/assets/styles/`、`frontend/src/assets/images/`、`frontend/src/assets/svg/`）—— 全局样式变量与所有静态资源
  - `frontend/src/components/core/` —— 58 个 `Art*` 框架组件
  - `frontend/src/views/index/index.vue` —— **布局容器**（本工程没有 `frontend/src/layout/` 目录，布局在这里）
  - `frontend/src/views/outside/Iframe.vue`、`frontend/src/views/auth/`、`frontend/src/views/exception/`
  - `frontend/src/router/` 下 7 个路由核心类文件（ComponentLoader / IframeRouteManager / MenuProcessor / RoutePermissionValidator / RouteRegistry / RouteTransformer / RouteValidator，2026-09-09 由 `router/core/` 扁平化）、`frontend/src/router/guards/`、`frontend/src/router/routes/staticRoutes.ts`、`frontend/src/router/routesAlias.ts`
  - `frontend/src/store/modules/`、`frontend/src/hooks/core/`、`frontend/src/utils/`、`frontend/src/directives/core/`、`frontend/src/enums/`、`frontend/src/plugins/`、`frontend/src/locales/index.ts`
  - `frontend/src/types/import/*.d.ts`（自动生成）、`frontend/src/mock/json/chinaMap.json`、`frontend/src/mock/upgrade/changeLog.ts`
  - `frontend/` 下的 `vite.config.ts`、`tsconfig.json`、`.env*`、`.env.development`、`.env.production`、`index.html`
- **绝对不要引入新的 npm 依赖**（任何第三方库先确认是否已有等价能力：ECharts、@vueuse/core、dayjs 等已在依赖中）。
- **绝对不要写 `<style scoped>` 去覆盖 Element Plus 默认样式**；需要调整一律走全局变量 / Tailwind 原子类 / `frontend/src/assets/styles/core/` 下的全局文件（且需人工确认后再改）。
- 不要改动 `frontend/package.json` 依赖版本、不要删改既有的 `frontend/pnpm-lock.yaml`。
- 不要删除第 5 节清单中任何现有文件——它们多为框架运行时依赖。

---

## 6. 交付前自检

- [ ] 未出现硬编码色值 / 圆角 / 阴影 / 间距像素值
- [ ] 未新增 `@keyframes` 或自定义 transition
- [ ] 页面根容器使用 `.page-content`，间距沿用 Tailwind 原子类
- [ ] 表格走 `useTable` + `ArtTable`，表单走 `ArtForm`，搜索走 `ArtSearchBar`
- [ ] 未修改第 5 节任何文件
- [ ] 未新增 npm 依赖
- [ ] `<script setup lang="ts">` 内含 `defineOptions({ name })`
- [ ] 新菜单已在 `router/modules/index.ts` 登记，文案已进 `locales/langs/*.json`
- [ ] 构建与类型检查通过（见下方 6.1）

### 6.1 ⚠️ 构建 / 类型检查（2026-09-09 已修复基线）

工程 `build` 脚本是 `vue-tsc --noEmit && vite build`。**历史上存在 20 个框架类型错误（依赖版本升级暴露），已于 2026-09-09 全部修复，当前基线为 0 错误，`pnpm build` 可完整通过。**

已修复的 5 个文件（后续如再报错即为新问题，不允许当作基线忽略）：

| 文件 | 原错误数 | 修复方式 |
|---|---|---|
| `frontend/src/components/core/forms/art-search-bar/index.vue` | 10 | `defineModel({ default: {} })` → `{ default: () => ({}) }` |
| `frontend/src/components/core/forms/art-form/index.vue` | 7 | 同上 |
| `frontend/src/utils/http/index.ts` | 1 | `contentType` 增加 `typeof === 'string'` 守卫 |
| `frontend/src/components/core/tables/art-table/index.vue` | 1 | `InstanceType<typeof ElTable>` → element-plus 官方 `TableInstance` |
| `frontend/src/components/core/layouts/art-menus/art-sidebar-menu/widget/SidebarSubmenu.vue` | 1 | 外链菜单 `:index` 不再传 `undefined`，改传 `getUniqueKey(item, index)` |

**正确做法：**

1. 验证构建：在 `frontend/` 目录执行 `pnpm build`（vue-tsc + vite build 一体，实测通过）
2. 验证类型：`frontend/node_modules/.bin/vue-tsc --noEmit`，**任何文件报错都需处理，不允许以"基线"为由忽略**
3. 修复类型错误时优先保持既有 API 语义不变；涉及框架层文件（第 5 节清单）仍需先提需求确认

> 本文 4.3 / 4.4 的骨架代码已实测：`vue-tsc` 零错误，`vite build` 通过。
