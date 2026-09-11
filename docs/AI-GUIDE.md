# AI 开发详细参考（AI-GUIDE）

> ⚠️ **本文件已于 2026-09-11 降级为「详细开发参考」，不再是规则真源。**
>
> **规则正文（六节强制准则全文）在工程根 `AGENTS.md` —— 那是唯一真源**，与本文件冲突时一律以 `AGENTS.md` 为准。
> 本文件只保留**规则正文之外的长内容**：可运行页面骨架代码、类型检查基线明细。
>
> **不要在本文件里新增或修改规则**——要改规则请改 `AGENTS.md`，否则会产生第二份副本并必然漂移。
>
> 变更原因见 `docs/需求规格/99-变更记录与文档约定.md` V6.57 / V6.58。

## 1. 本文件保留什么

| 章节 | 内容 | 为什么留在这里而不进 `AGENTS.md` |
|---|---|---|
| §2 | 列表页完整骨架代码 | 长代码块，需要时按需读取；`AGENTS.md` 受 32 KiB 体量约束 |
| §3 | 表单弹窗完整骨架代码 | 同上 |
| §4 | 新增业务模块标准动作 | 步骤清单，配合骨架使用 |
| §5 | 类型检查基线明细 | 工程特定的历史修复记录，非通用规则 |

**规则类内容（需求沟通 / 样式规则 / 组件规则 / 参考模板索引 / 禁止事项 / 交付前自检）全部在 `AGENTS.md`，本文件不再重复。**

---

## 2. 列表页骨架

> 已实测：`vue-tsc` 零错误，`vite build` 通过。

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
    // 见 §3：打开 ElDialog + ArtForm
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

---

## 3. 表单弹窗骨架

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

---

## 4. 新增一个业务模块的标准动作

1. `frontend/src/views/<module>/index.vue`（列表）+ `frontend/src/views/<module>/modules/xxx-dialog.vue`（表单），骨架照抄本文 §2 / §3
2. `frontend/src/api/<module>.ts` 定义接口，类型写进 `frontend/src/types/api/api.d.ts`
3. `frontend/src/router/modules/<module>.ts` 写菜单（`component: '/index/index'` 为一级菜单布局，子项 `component: '/<module>/index'`）
4. 在 `frontend/src/router/modules/index.ts` 的 import 与 `routeModules` 数组中登记
5. 文案加进 `frontend/src/locales/langs/zh.json` 与 `en.json`
6. 按 `AGENTS.md` 第 6 节验证构建与类型

---

## 5. 类型检查基线明细

> 规则口径见 `AGENTS.md` 第 6.1 节；本节只保留历史修复明细。

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
3. 修复类型错误时优先保持既有 API 语义不变；涉及框架层文件（`AGENTS.md` 第 5 节清单）仍需先提需求确认
