<!-- 专题管理：列表（FR-TOP-001~008：筛选选定即查、待办醒目数字穿透详情、导入导出 Excel）
     与模块管理（views/module/index.vue）同构，差异：无「归属产品」「模块分类」两列/两筛选项 -->
<template>
  <div class="page-content flex flex-col" style="height: var(--art-full-height)">
    <!-- 只读提示条（无 topic:edit 权限） -->
    <ElAlert
      v-if="!canEdit"
      type="warning"
      :closable="false"
      show-icon
      :title="t('system.topic.readOnlyTip')"
      class="mb-4 shrink-0"
    />

    <!-- 查询控件由表头漏斗筛选承载（列 useHeaderSlot + #<prop>-header 插槽），
         查询条件与联动逻辑同模块管理（searchForm / buildSearchParams / handleSearch 复用）
    <ArtSearchBar
      v-model="searchForm"
      :items="searchItems"
      class="mt-2"
      @search="handleSearch"
      @reset="handleReset"
    />
    -->

    <!-- 全部工具按钮与表格工具栏同一行（#left 插槽） -->
    <ArtTableHeader
      :layout="'search,refresh,size,columns,settings'"
      class="mt-2 shrink-0"
      v-model:columns="columnChecks"
      :loading="loading"
      @refresh="refreshData"
    >
      <template #left>
        <div class="flex flex-nowrap items-center">
          <ElButton v-if="canEdit" type="primary" v-ripple @click="openForm()">
            <ArtSvgIcon icon="ri:add-line" class="mr-1" />
            {{ t('common.add') }}
          </ElButton>
          <!-- 导入需编辑权限；导出仅查看权限即可（topic.view） -->
          <ElButton v-if="canEdit" v-ripple @click="triggerImport">
            <ArtSvgIcon icon="ri:upload-2-line" class="mr-1" />
            {{ t('system.topic.importButton') }}
          </ElButton>
          <ElButton v-ripple @click="handleExport">
            <ArtSvgIcon icon="ri:download-2-line" class="mr-1" />
            {{ t('system.topic.exportButton') }}
          </ElButton>
          <ElButton v-ripple @click="handleReset">
            <ArtSvgIcon icon="ri:filter-off-line" class="mr-1" />
            {{ t('common.resetFilter') }}
          </ElButton>
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
        <!-- 表头筛选（所有数据列均带漏斗） -->
        <template #name-header>
          <HeaderFilter
            :label="t('system.topic.title')"
            v-model="searchForm.kw"
            :placeholder="t('system.topic.titlePlaceholder')"
            @change="handleSearch"
          />
        </template>

        <template #dept-header>
          <HeaderFilter
            :label="t('system.topic.dept')"
            v-model="searchForm.dept"
            :options="toOptions(topicOptions.depts)"
            @change="handleSearch"
          />
        </template>

        <template #team-header>
          <HeaderFilter
            :label="t('system.topic.team')"
            v-model="searchForm.team"
            :options="toOptions(topicOptions.teams)"
            @change="handleSearch"
          />
        </template>

        <template #owner-header>
          <HeaderFilter
            :label="t('system.topic.owner')"
            v-model="searchForm.owner"
            :options="toOptions(topicOptions.owners)"
            @change="handleSearch"
          />
        </template>

        <template #rd-header>
          <HeaderFilter
            :label="t('system.topic.rd')"
            v-model="searchForm.rd"
            :placeholder="t('system.topic.rd')"
            @change="handleSearch"
          />
        </template>

        <template #test-header>
          <HeaderFilter
            :label="t('system.topic.test')"
            v-model="searchForm.test"
            :placeholder="t('system.topic.test')"
            @change="handleSearch"
          />
        </template>

        <template #status-header>
          <HeaderFilter
            :label="t('system.topic.status')"
            v-model="searchForm.status"
            :options="toOptions(topicOptions.statuses)"
            @change="handleSearch"
          />
        </template>

        <template #todoCount-header>
          <HeaderFilter
            :label="t('system.topic.todo')"
            v-model="searchForm.todoCount"
            mode="number"
            :placeholder="t('system.topic.todo')"
            @change="handleSearch"
          />
        </template>

        <!-- 专题名称：点击进入详情 -->
        <template #name="{ row }">
          <ElLink type="primary" :underline="false" @click="goDetail(row)">
            <span class="font-medium">{{ row.name }}</span>
          </ElLink>
        </template>

        <!-- 专题状态 -->
        <template #status="{ row }">
          <ElTag size="small" :type="statusTagType(row.status)">{{ row.status }}</ElTag>
        </template>

        <!-- 待办任务：醒目数字，点击穿透详情进度看板 -->
        <template #todoCount="{ row }">
          <ElLink :underline="false" @click="goDetail(row, 'kanban')">
            <span
              v-if="row.todoCount > 0"
              class="text-base font-bold"
              :class="row.todoCount >= 10 ? 'text-danger' : 'text-warning'"
            >
              {{ row.todoCount }}
            </span>
            <span v-else class="text-g-400">0</span>
          </ElLink>
        </template>

        <!-- 研发负责人（多） -->
        <template #rd="{ row }">
          <span class="text-g-600">{{ (row.rd ?? []).join('、') || '—' }}</span>
        </template>

        <template #operation="{ row }">
          <div class="flex items-center">
            <ArtButtonTable type="view" @click="goDetail(row)" />
            <ArtButtonTable v-if="canEdit && row.canEdit" type="edit" @click="openForm(row)" />
            <ArtButtonTable v-if="canEdit && row.canEdit" type="delete" @click="handleDelete(row)" />
          </div>
        </template>
      </ArtTable>
    </div>

    <!-- 导入文件选择（隐藏） -->
    <input ref="importInputRef" type="file" accept=".xlsx" class="hidden" @change="handleImportFile" />

    <TopicDialog ref="topicDialogRef" @success="refreshUpdate" />
  </div>
</template>

<script setup lang="ts">
  import {
    fetchDeleteTopic,
    fetchImportTopic,
    fetchTopicDetail,
    fetchTopicOptions,
    fetchTopicPage,
    topicExportUrl,
    fetchBatchDeleteTopics
} from '@/api/topic'
  import { fetchGetUserInfo } from '@/api/auth'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { useRouter } from 'vue-router'
  import { useUserStore } from '@/store/modules/user'
  import type { ColumnOption } from '@/types'
  import TopicDialog from './modules/topic-dialog.vue'
  import HeaderFilter from '@/components/business/header-filter/index.vue'
  import { buildFilters } from '@/components/business/header-filter/filters'

  defineOptions({ name: 'TopicIndex' })

  const { t } = useI18n()
  const router = useRouter()
  const userStore = useUserStore()

  /** 权限点判定（与模块页同款：admin 全量放行 + 前端模式 mock 兜底） */
  const buttons = computed(() => userStore.info.buttons ?? [])
  const isSuper = computed(() => (userStore.info.roles ?? []).includes('R_SUPER'))
  const hasTopicCodes = computed(() => buttons.value.some((code) => code.startsWith('topic:')))
  const canEdit = computed(() => isSuper.value || !hasTopicCodes.value || buttons.value.includes('topic:edit'))

  /** 搜索表单（与 useTable 的 searchParams 解耦，避免分页字段被清空） */
  const searchForm = ref<Record<string, any>>({})

  /** 筛选候选值 */
  const topicOptions = ref<Api.Topic.TopicOptions>({
    statuses: [],
    depts: [],
    teams: [],
    owners: []
  })

  /** 候选值 → 表头筛选组件选项（枚举列） */
  const toOptions = (values: string[]) => values.map((value) => ({ label: value, value }))

  const searchItems = computed(() => {
    const opts = (values: string[]) => values.map((value) => ({ label: value, value }))
    return [
      {
        key: 'kw',
        label: t('system.topic.title'),
        type: 'input',
        props: { placeholder: t('system.topic.titlePlaceholder'), clearable: true }
      },
      {
        key: 'dept',
        label: t('system.topic.dept'),
        type: 'select',
        props: { placeholder: t('system.topic.deptPlaceholder'), clearable: true, options: opts(topicOptions.value.depts) }
      },
      {
        key: 'team',
        label: t('system.topic.team'),
        type: 'select',
        props: { placeholder: t('system.topic.teamPlaceholder'), clearable: true, options: opts(topicOptions.value.teams) }
      },
      {
        key: 'owner',
        label: t('system.topic.owner'),
        type: 'select',
        props: { placeholder: t('system.topic.ownerPlaceholder'), clearable: true, filterable: true, options: opts(topicOptions.value.owners) }
      },
      {
        key: 'status',
        label: t('system.topic.status'),
        type: 'select',
        props: { placeholder: t('system.topic.statusPlaceholder'), clearable: true, options: opts(topicOptions.value.statuses) }
      }
    ]
  })

  const statusTagType = (status: string) => {
    switch (status) {
      case '活跃':
        return 'success'
      default:
        return 'info'
    }
  }

  const columnsFactory = () =>
    [
      // 2026-09-11 用户需求：批量删除需要行多选
      { type: 'selection', width: 50 },
      // 查询条件内嵌至列头（useHeaderSlot），对应 #<prop>-header 插槽
      // 默认不展示 测试负责人/研发负责人（可通过表头「列选」开启）；列宽 minWidth 弹性自适应
      { prop: 'name', label: t('system.topic.title'), minWidth: 140, useSlot: true, useHeaderSlot: true },
      { prop: 'dept', label: t('system.topic.dept'), minWidth: 105, useHeaderSlot: true },
      { prop: 'team', label: t('system.topic.team'), minWidth: 105, useHeaderSlot: true },
      { prop: 'owner', label: t('system.topic.owner'), minWidth: 105, useHeaderSlot: true },
      { prop: 'rd', label: t('system.topic.rd'), minWidth: 110, visible: false, useSlot: true, useHeaderSlot: true },
      { prop: 'test', label: t('system.topic.test'), minWidth: 100, visible: false, useHeaderSlot: true },
      { prop: 'status', label: t('system.topic.status'), minWidth: 95, useSlot: true, useHeaderSlot: true },
      { prop: 'todoCount', label: t('system.topic.todo'), minWidth: 90, useSlot: true, useHeaderSlot: true },
      {
        prop: 'operation',
        label: t('common.operation'),
        width: 165,
        fixed: 'right',
        useSlot: true
      }
    ] as ColumnOption<Api.Topic.TopicListItem>[]

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
    const result = await fetchBatchDeleteTopics(selectedIds.value)
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
    core: { apiFn: fetchTopicPage, apiParams: { size: 50 } }
  })

  /** 查询：替换搜索条件并回到第一页（表头漏斗选定即触发） */
  const handleSearch = () => {
    replaceSearchParams(buildSearchParams(searchForm.value))
    fetchData()
  }

  /** 重置：清空条件（useTable 内部会自动重新请求） */
  const handleReset = () => {
    searchForm.value = {}
    resetSearchParams()
  }

  /** 搜索表单 → 接口参数（去掉空值；多选数组用逗号连接，后端按 List 接收） */
  const buildSearchParams = (form: Record<string, any>): Record<string, any> => {
    const params: Record<string, any> = {}
    for (const key of ['kw', 'dept', 'team', 'owner', 'status']) {
      const value = form[key]
      if (Array.isArray(value)) {
        if (value.length) params[key] = value.join(',')
      } else if (value) {
        params[key] = value
      }
    }
    // 通用 filters 参数承载漏斗列筛选
    const filters = buildFilters(form, { 'rd': 'like', 'test': 'like', 'todoCount': 'gte' })
    if (filters) params.filters = filters
    return params
  }

  // ==================== 操作 ====================

  const goDetail = (row: Api.Topic.TopicListItem, tab?: string) => {
    router.push({ path: `/topic/detail/${row.id}`, query: tab ? { tab } : undefined })
  }

  const topicDialogRef = useTemplateRef<{ open: (row?: Api.Topic.TopicListItem) => void }>('topicDialogRef')

  const openForm = (row?: Api.Topic.TopicListItem) => {
    topicDialogRef.value?.open(row)
  }

  /** 删除：二次确认，明示将级联删除的任务数与文档数（先取详情统计） */
  const handleDelete = async (row: Api.Topic.TopicListItem) => {
    const detail = await fetchTopicDetail(row.id)
    const tip =
      detail.totalTaskCount > 0 || detail.docCount > 0
        ? t('system.topic.deleteCascadedTips', { tasks: detail.totalTaskCount, docs: detail.docCount })
        : t('common.deleteTips')
    await ElMessageBox.confirm(tip, t('common.tips'), { type: 'warning' })
    await fetchDeleteTopic(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    refreshUpdate()
  }

  /** 导出：当前筛选结果全量（UTF-8 BOM，Excel 打开中文不乱码；token 走 ?token=） */
  const handleExport = () => {
    const url = topicExportUrl(buildSearchParams(searchForm.value), userStore.accessToken ?? '')
    window.open(url, '_blank')
  }

  /** 导入：选择 Excel(.xlsx) 文件后上传（按表头映射，非法字典值忽略） */
  const importInputRef = useTemplateRef<HTMLInputElement>('importInputRef')

  const triggerImport = () => {
    importInputRef.value?.click()
  }

  const handleImportFile = async (event: Event) => {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) return
    const result = await fetchImportTopic(file)
    ElMessage.success(t('system.topic.importResult', { created: result.created, skipped: result.skipped }))
    refreshUpdate()
  }

  onMounted(async () => {
    topicOptions.value = await fetchTopicOptions()
    // 前端模式守卫不拉取真实用户信息：已登录时主动刷新一次（与模块/任务页一致）
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
