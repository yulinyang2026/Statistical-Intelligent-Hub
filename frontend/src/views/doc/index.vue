<!-- 文档中心（FR-DOC-001~006）：全局跨对象标题检索、Markdown 编辑/预览、只读权限标识 -->
<template>
  <div class="page-content flex flex-col" style="height: var(--art-full-height)">
    <!-- 工具条：新增 / 重置筛选 -->
    <div class="flex flex-wrap items-center justify-between gap-2 shrink-0">
      <div class="flex items-center">
        <ElButton v-if="canEdit" type="primary" v-ripple @click="openForm()">
          <ArtSvgIcon icon="ri:add-line" class="mr-1" />
          {{ t('common.add') }}
        </ElButton>
        <!-- 2026-09-10 查询条件移到表头后，保留统一重置入口 -->
        <ElButton v-ripple @click="handleReset">
          <ArtSvgIcon icon="ri:filter-off-line" class="mr-1" />
          {{ t('common.resetFilter') }}
        </ElButton>
      </div>
      <span class="text-xs text-g-500">{{ t('system.doc.centerTip') }}</span>
    </div>

    <!-- 2026-09-10 按需求注释：查询控件改为表头漏斗筛选（列 useHeaderSlot + #<prop>-header 插槽），
         查询条件与联动逻辑不变（searchForm / handleSearch 复用），原查询组件代码保留备查
    <ArtSearchBar
      v-model="searchForm"
      :items="searchItems"
      class="mt-2"
      @search="handleSearch"
      @reset="handleReset"
    />
    -->

    <ArtTableHeader
      :layout="'search,refresh,size,columns,settings'"
      class="mt-2 shrink-0"
      v-model:columns="columnChecks"
      :loading="loading"
      @refresh="refreshData"
    >
      <template #left>
        <div class="flex flex-nowrap items-center">
          <!-- 2026-09-11 用户需求：批量删除（仅超级管理员可见，服务端按 batch:delete 二次判权） -->
          <ElButton
            v-if="canBatchDelete"
            type="danger"
            :disabled="!selectedIds.length"
            v-ripple
            @click="handleBatchDelete"
          >
            <ArtSvgIcon icon="ri:delete-bin-6-line" class="mr-1" />
            {{ t('common.batchDelete') }}
          </ElButton>
        </div>
      </template>
    </ArtTableHeader>

    <div class="flex-1 min-h-0">
      <ArtTable
        ref="tableRef"
        @selection-change="handleSelectionChange"
        :show-table-header="false"
        :loading="loading"
        :data="data"
        :columns="columns"
        row-key="id"
        :pagination="pagination"
        :pagination-options="{ pageSizes: [50, 100, 150], align: 'right' }"
        @pagination:size-change="handleSizeChange"
        @pagination:current-change="handleCurrentChange"
      >
      <!-- 表头筛选（2026-09-10 用户需求：所有字段均带漏斗） -->
      <template #title-header>
        <HeaderFilter
          :label="t('system.doc.title')"
          v-model="searchForm.kw"
          :placeholder="t('system.doc.searchPlaceholder')"
          @change="handleSearch"
        />
      </template>

      <template #belongLabel-header>
        <HeaderFilter
          :label="t('system.doc.belong')"
          v-model="searchForm.belongLabel"
          :placeholder="t('system.doc.belong')"
          @change="handleSearch"
        />
      </template>

      <template #summary-header>
        <HeaderFilter
          :label="t('system.doc.summary')"
          v-model="searchForm.summary"
          :placeholder="t('system.doc.summary')"
          @change="handleSearch"
        />
      </template>

      <template #updatedBy-header>
        <HeaderFilter
          :label="t('system.doc.updatedBy')"
          v-model="searchForm.updatedBy"
          :placeholder="t('system.doc.updatedBy')"
          @change="handleSearch"
        />
      </template>

      <template #updatedAt-header>
        <HeaderFilter
          :label="t('system.doc.updatedAt')"
          v-model="searchForm.updatedAt"
          mode="daterange"
          @change="handleSearch"
        />
      </template>

      <template #wordCount-header>
        <HeaderFilter
          :label="t('system.doc.wordCount')"
          v-model="searchForm.wordCount"
          mode="number"
          :placeholder="t('system.doc.wordCount')"
          @change="handleSearch"
        />
      </template>
        <!-- 标题（带图标） -->
        <template #title="{ row }">
          <div class="flex items-center gap-1.5">
            <ArtSvgIcon icon="ri:file-mark-line" class="text-theme" />
            <ElLink type="primary" :underline="false" @click="openForm(row, true)">
              <span class="font-medium">{{ row.title }}</span>
            </ElLink>
          </div>
        </template>

        <!-- 归属 -->
        <template #belongLabel="{ row }">
          <ElTag size="small" type="info">{{ row.belongLabel || '—' }}</ElTag>
        </template>

        <!-- 只读预览标识（无编辑权限，FR-DOC-006） -->
        <template #operation="{ row }">
          <div class="flex items-center">
            <span v-if="!row.canEdit" class="mr-2 text-xs text-g-400">{{ t('system.doc.readOnlyPreview') }}</span>
            <ArtButtonTable type="view" @click="openForm(row, true)" />
            <ArtButtonTable v-if="row.canEdit" type="edit" @click="openForm(row)" />
            <ArtButtonTable v-if="row.canDelete" type="delete" @click="handleDelete(row)" />
          </div>
        </template>
      </ArtTable>

    </div>

    <DocDialog ref="docDialogRef" @success="refreshUpdate" />
  </div>
</template>

<script setup lang="ts">
  import {
  fetchDeleteDoc, fetchDocPage,
  fetchBatchDeleteDocs
} from '@/api/doc'
  import { fetchGetUserInfo } from '@/api/auth'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { useUserStore } from '@/store/modules/user'
  import type { ColumnOption } from '@/types'
  import DocDialog from './modules/doc-dialog.vue'
  import HeaderFilter from '@/components/business/header-filter/index.vue'
  import { buildFilters } from '@/components/business/header-filter/filters'

  defineOptions({ name: 'DocIndex' })

  const { t } = useI18n()
  const userStore = useUserStore()

  /** 权限点判定（与任务页同款兜底逻辑；按钮显隐同时参考行级 canEdit/canDelete） */
  const buttons = computed(() => userStore.info.buttons ?? [])
  const isSuper = computed(() => (userStore.info.roles ?? []).includes('R_SUPER'))
  const hasDocCodes = computed(() => buttons.value.some((code) => code.startsWith('doc:')))
  const canEdit = computed(() => isSuper.value || !hasDocCodes.value || buttons.value.includes('doc:edit'))

  /** 搜索表单（与 useTable 的 searchParams 解耦） */
  const searchForm = ref<Record<string, any>>({})

  const searchItems = computed(() => [
    {
      key: 'kw',
      label: t('system.doc.title'),
      type: 'input',
      props: { placeholder: t('system.doc.searchPlaceholder'), clearable: true }
    }
  ])

  const columnsFactory = () =>
    [
      // 2026-09-11 用户需求：批量删除需要行多选
      { type: 'selection', width: 50 },
      // 2026-09-10：查询条件内嵌至列头（useHeaderSlot），对应 #title-header 插槽
      { prop: 'title', label: t('system.doc.title'), minWidth: 200, useSlot: true, useHeaderSlot: true },
      { prop: 'belongLabel', label: t('system.doc.belong'), minWidth: 130, useSlot: true, useHeaderSlot: true },
      { prop: 'summary', label: t('system.doc.summary'), minWidth: 200, useHeaderSlot: true },
      { prop: 'updatedBy', label: t('system.doc.updatedBy'), minWidth: 95, useHeaderSlot: true },
      { prop: 'updatedAt', label: t('system.doc.updatedAt'), minWidth: 150, useHeaderSlot: true },
      { prop: 'wordCount', label: t('system.doc.wordCount'), minWidth: 95, useHeaderSlot: true },
      {
        prop: 'operation',
        label: t('common.operation'),
        width: 165,
        fixed: 'right',
        useSlot: true
      }
    ] as ColumnOption<Api.Doc.DocListItem>[]

  const { columns, columnChecks } = useTableColumns(columnsFactory)

  // ==================== 批量删除（2026-09-11 用户需求：仅超级管理员可见） ====================

  const tableRef = useTemplateRef<{ elTableRef: { clearSelection: () => void } }>('tableRef')
  /** 已勾选行 id */
  const selectedIds = ref<string[]>([])
  /** 超级管理员或持 batch:delete 权限点 */
  const canBatchDelete = computed(
    () => isSuper.value || buttons.value.includes('batch:delete')
  )

  const handleSelectionChange = (rows: any[]) => {
    selectedIds.value = (rows || []).map((row) => row.id)
  }

  const handleBatchDelete = async () => {
    if (!selectedIds.value.length) {
      ElMessage.warning(t('common.batchDeleteNoSelection'))
      return
    }
    await ElMessageBox.confirm(
      t('common.batchDeleteTips', { n: selectedIds.value.length }),
      t('common.tips'),
      { type: 'warning' }
    )
    const result = await fetchBatchDeleteDocs(selectedIds.value)
    ElMessage.success(t('common.batchDeleteResult', { n: result.deleted }))
    selectedIds.value = []
    tableRef.value?.elTableRef?.clearSelection()
    refreshUpdate()
  }

  const {
    data,
    loading,
    pagination,
    replaceSearchParams,
    resetSearchParams,
    handleSizeChange,
    handleCurrentChange,
    fetchData,
    refreshData,
    refreshUpdate
  } = useTable({
    core: { apiFn: fetchDocPage, apiParams: { size: 50 } }
  })

  const handleSearch = () => {
    const params: Record<string, any> = {}
    if (searchForm.value.kw) params.kw = searchForm.value.kw
    // 2026-09-10 用户需求：新增漏斗列的筛选统一走通用 filters 参数
    const filters = buildFilters(searchForm.value, {
      belongLabel: 'like',
      summary: 'like',
      updatedBy: 'like',
      updatedAt: 'between',
      wordCount: 'gte'
    })
    if (filters) params.filters = filters
    replaceSearchParams(params)
    fetchData()
  }

  const handleReset = () => {
    searchForm.value = {}
    resetSearchParams()
  }

  // ==================== 操作 ====================

  const docDialogRef = useTemplateRef<{
    open: (options: { row?: Api.Doc.DocListItem; view?: boolean }) => void
  }>('docDialogRef')

  const openForm = (row?: Api.Doc.DocListItem, view = false) => {
    docDialogRef.value?.open({ row, view })
  }

  /** 删除（二次确认，FR-DOC-004） */
  const handleDelete = async (row: Api.Doc.DocListItem) => {
    await ElMessageBox.confirm(t('common.deleteTips'), t('common.tips'), { type: 'warning' })
    await fetchDeleteDoc(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    refreshUpdate()
  }

  onMounted(async () => {
    // 前端模式守卫不拉取真实用户信息：已登录时主动刷新一次（与任务页一致）
    if (userStore.accessToken) {
      try {
        const info = await fetchGetUserInfo()
        userStore.setUserInfo(info)
      } catch {
        // 忽略：刷新失败时保持现有权限判定
      }
    }
  })
</script>
