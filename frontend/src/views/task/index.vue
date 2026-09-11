<!-- 任务管理：列表 / 看板双视图（08a 看板：6 维度分组交叉列 + 内容筛选 + 主维度拖拽写回 + 工时内联编辑） -->
<template>
  <div class="page-content flex flex-col" style="height: var(--art-full-height)">
    <!-- 只读提示条（无 task:edit 权限） -->
    <ElAlert
      v-if="!canEdit"
      type="warning"
      :closable="false"
      show-icon
      :title="t('system.task.readOnlyTip')"
      class="mb-4 shrink-0"
    />

    <!-- ① 工具栏：视图切换（2026-09-10 按需求：居中显示；列表视图的新增/重置筛选已并入表格工具栏行；看板无表头，保留新增入口） -->
    <div class="flex flex-wrap items-center gap-2 shrink-0">
      <div class="flex-1"></div>
      <ElRadioGroup v-model="viewMode">
        <ElRadioButton value="list">{{ t('system.task.listView') }}</ElRadioButton>
        <ElRadioButton value="kanban">{{ t('system.task.kanbanView') }}</ElRadioButton>
      </ElRadioGroup>
      <div class="flex flex-1 items-center justify-end gap-3">
        <ElButton v-if="canCreate && viewMode === 'kanban'" type="primary" v-ripple @click="openForm()">
          <ArtSvgIcon icon="ri:add-line" class="mr-1" />
          {{ t('common.add') }}
        </ElButton>
        <span v-if="viewMode === 'kanban'" class="text-xs text-g-500">
          {{ canViewAll ? t('system.task.kanbanScopeAll') : t('system.task.kanbanScopeMine') }}
        </span>
      </div>
    </div>

    <!-- 2026-09-10 按需求注释：查询控件改为表头字段内嵌（列 useHeaderSlot + #<prop>-header 插槽），
         查询条件与联动逻辑不变（searchForm / buildSearchParams / handleSearch 复用），原查询组件代码保留备查
    <ArtSearchBar
      v-if="viewMode === 'list'"
      v-model="searchForm"
      :items="searchItems"
      class="mt-2"
      @search="handleSearch"
      @reset="handleReset"
    />
    -->

    <!-- 列表视图 -->
    <template v-if="viewMode === 'list'">
      <!-- 2026-09-10 按需求：新增 / 重置筛选与表格工具栏同一行（#left 插槽） -->
      <ArtTableHeader
        :layout="'search,refresh,size,columns,settings'"
        class="mt-2 shrink-0"
        v-model:columns="columnChecks"
        :loading="loading"
        @refresh="refreshData"
      >
        <template #left>
          <div class="flex flex-nowrap items-center">
            <ElButton v-if="canCreate" type="primary" v-ripple @click="openForm()">
              <ArtSvgIcon icon="ri:add-line" class="mr-1" />
              {{ t('common.add') }}
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
          :tree-props="{ children: 'children' }"
          default-expand-all
          @pagination:size-change="handleSizeChange"
          @pagination:current-change="handleCurrentChange"
        >
        <!-- 表头筛选（2026-09-10 用户需求：所有字段均带漏斗；文本/候选多选/数值≥/日期区间） -->
        <template #belongLabel-header>
          <HeaderFilter
            :label="t('system.task.belong')"
            v-model="searchForm.belong"
            :options="belongHeaderOptions"
            @change="handleSearch"
          />
        </template>

        <template #title-header>
          <HeaderFilter
            :label="t('system.task.title')"
            v-model="searchForm.kw"
            :placeholder="t('system.task.titlePlaceholder')"
            @change="handleSearch"
          />
        </template>

        <template #assignee-header>
          <HeaderFilter
            :label="t('system.task.assignee')"
            v-model="searchForm.assignee"
            :options="toOptions(taskOptions.assignees)"
            @change="handleSearch"
          />
        </template>

        <template #priority-header>
          <HeaderFilter
            :label="t('system.task.priority')"
            v-model="searchForm.priority"
            :options="toOptions(taskOptions.priorities)"
            @change="handleSearch"
          />
        </template>

        <template #hours-header>
          <HeaderFilter
            :label="t('system.task.hours')"
            v-model="searchForm.hours"
            mode="number"
            :placeholder="t('system.task.hours')"
            @change="handleSearch"
          />
        </template>

        <template #planDate-header>
          <HeaderFilter
            :label="t('system.task.planDate')"
            v-model="searchForm.planDate"
            mode="daterange"
            @change="handleSearch"
          />
        </template>

        <template #status-header>
          <HeaderFilter
            :label="t('system.task.status')"
            v-model="searchForm.status"
            :options="toOptions(taskOptions.statuses)"
            @change="handleSearch"
          />
        </template>

        <template #group-header>
          <HeaderFilter
            :label="t('system.task.group')"
            v-model="searchForm.group"
            :options="toOptions(taskOptions.groups)"
            @change="handleSearch"
          />
        </template>
          <template #title="{ row }">
            <ElTooltip :content="row.desc" placement="top" :disabled="!row.desc" :show-after="200">
              <div class="flex items-center gap-1.5">
                <ElTag v-if="row.parentId" size="small" type="info">{{ t('system.task.childBadge') }}</ElTag>
                <span class="font-medium">{{ row.title }}</span>
                <ArtSvgIcon v-if="row.desc" icon="ri:file-text-line" class="text-sm text-theme" />
              </div>
            </ElTooltip>
          </template>

          <template #assignee="{ row }">
            <div class="flex items-center gap-1.5">
              <ElAvatar :size="24">{{ row.assignee?.charAt(0) }}</ElAvatar>
              <span>{{ row.assignee }}</span>
            </div>
          </template>

          <template #priority="{ row }">
            <ElTag size="small" :type="priorityTagType(row.priority)">{{ row.priority }}</ElTag>
          </template>

          <template #status="{ row }">
            <ElTag size="small" :type="statusTagType(row.status)">{{ row.status }}</ElTag>
          </template>

          <template #operation="{ row }">
            <div class="flex items-center">
              <ArtButtonTable v-if="canEdit" type="edit" @click="openForm(row)" />
              <ArtButtonTable
                v-if="canCreate && !row.parentId"
                type="view"
                icon="ri:list-indefinite"
                @click="openSubtask(row)"
              />
              <ArtButtonTable v-if="canEdit" type="delete" @click="handleDelete(row)" />
            </div>
          </template>
        </ArtTable>

      </div>
    </template>

    <!-- 看板视图（定高区域内滚动；2026-09-10 页面改定高布局） -->
    <div v-else class="flex-1 min-h-0 overflow-auto">
      <KanbanView
        :tasks="kanbanTasks"
        :loading="kanbanLoading"
        :readonly="!canEdit"
        @open-form="openForm"
        @field-change="handleFieldChange"
        @hours-commit="handleHoursCommit"
      />
    </div>

    <TaskDialog ref="taskDialogRef" @success="handleDialogSuccess" />
  </div>
</template>

<script setup lang="ts">
  import {
  fetchDeleteTask, fetchPatchTask, fetchTaskOptions, fetchTaskPage,
  fetchBatchDeleteTasks
} from '@/api/task'
  import { fetchGetUserInfo } from '@/api/auth'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { useUserStore } from '@/store/modules/user'
  import type { ColumnOption } from '@/types'
  import TaskDialog from './modules/task-dialog.vue'
  import KanbanView from './modules/kanban-view.vue'
  import HeaderFilter from '@/components/business/header-filter/index.vue'
  import { buildFilters } from '@/components/business/header-filter/filters'

  defineOptions({ name: 'TaskIndex' })

  const { t } = useI18n()
  const userStore = useUserStore()

  /** 权限点（后端 /api/user/info buttons 下发；按权限点判定） */
  const buttons = computed(() => userStore.info.buttons ?? [])
  /** 超级管理员全量放行（与后端 PermissionService 对 R_SUPER 的规则一致：admin 不受数据/功能权限限制） */
  const isSuper = computed(() => (userStore.info.roles ?? []).includes('R_SUPER'))
  /**
   * 前端模式（VITE_ACCESS_MODE=frontend）守卫不拉取真实用户信息，mock buttons 不含 task:* 码：
   * 无 task:* 码时视为全量放行（服务端仍按登录态二次校验），避免操作按钮被误隐藏。
   */
  const hasTaskCodes = computed(() => buttons.value.some((code) => code.startsWith('task:')))
  const canCreate = computed(() => isSuper.value || !hasTaskCodes.value || buttons.value.includes('task:create'))
  const canEdit = computed(() => isSuper.value || !hasTaskCodes.value || buttons.value.includes('task:edit'))
  const canViewAll = computed(() => isSuper.value || !hasTaskCodes.value || buttons.value.includes('task:view-all'))

  /** 搜索表单（与 useTable 的 searchParams 解耦，避免分页字段被清空） */
  const searchForm = ref<Record<string, any>>({})

  /** 筛选候选值 */
  const taskOptions = ref<Api.Task.TaskOptions>({
    belongOptions: [],
    subsystems: [],
    deadlines: [],
    statuses: [],
    priorities: [],
    assignees: [],
    groups: []
  })

  /** 候选值 → 表头筛选组件选项（枚举列） */
  const toOptions = (values: string[]) => values.map((value) => ({ label: value, value }))

  const viewMode = ref<'list' | 'kanban'>('list')

  const searchItems = computed(() => [
    {
      key: 'kw',
      label: t('system.task.title'),
      type: 'input',
      props: { placeholder: t('system.task.titlePlaceholder'), clearable: true }
    },
    {
      key: 'belong',
      label: t('system.task.belong'),
      type: 'select',
      props: {
        placeholder: t('system.task.belongPlaceholder'),
        clearable: true,
        options: [
          { label: t('system.task.belongNone'), value: '' },
          ...taskOptions.value.belongOptions.map((option) => ({
            label: `${belongTypeLabel(option.type)}·${option.name}`,
            value: `${option.type}:${option.id}`
          }))
        ]
      }
    },
    /* 2026-09-10 按需求注释：子系统筛选项去掉（表头无对应列，用户确认不加子系统列）
    {
      key: 'subsystem',
      label: t('system.task.subsystem'),
      type: 'select',
      props: {
        placeholder: t('system.task.subsystemPlaceholder'),
        clearable: true,
        options: subsystemFilterOptions.value
      }
    },
    */
    {
      key: 'status',
      label: t('system.task.status'),
      type: 'select',
      props: {
        placeholder: t('system.task.statusPlaceholder'),
        clearable: true,
        options: taskOptions.value.statuses.map((status) => ({ label: status, value: status }))
      }
    },
    {
      key: 'priority',
      label: t('system.task.priority'),
      type: 'select',
      props: {
        placeholder: t('system.task.priorityPlaceholder'),
        clearable: true,
        options: taskOptions.value.priorities.map((priority) => ({ label: priority, value: priority }))
      }
    },
    {
      key: 'assignee',
      label: t('system.task.assignee'),
      type: 'select',
      props: {
        placeholder: t('system.task.assigneePlaceholder'),
        clearable: true,
        options: taskOptions.value.assignees.map((name) => ({ label: name, value: name }))
      }
    },
    {
      key: 'group',
      label: t('system.task.group'),
      type: 'select',
      props: {
        placeholder: t('system.task.groupPlaceholder'),
        clearable: true,
        options: taskOptions.value.groups.map((group) => ({ label: group, value: group }))
      }
    }
  ])

  /** 归属列头筛选候选（type:id 组合值与任务表单一致） */
  const belongHeaderOptions = computed(() =>
    taskOptions.value.belongOptions.map((option) => ({
      label: `${belongTypeLabel(option.type)}·${option.name}`,
      value: `${option.type}:${option.id}`
    }))
  )

  /* 2026-09-10 按需求注释：任务列表「子系统」筛选去掉（任务表单的子系统关联保留，FR-PROJ-014 修订）
  const subsystemFilterOptions = computed(() => {
    const belong = searchForm.value.belong as string | undefined
    const projectId = belong?.startsWith('project:') ? belong.slice(8) : ''
    return taskOptions.value.subsystems
      .filter((item) => !projectId || item.projectId === projectId)
      .map((item) => ({ label: item.name, value: item.id }))
  })
  */

  const belongTypeLabel = (type: string) => {
    switch (type) {
      case 'project':
        return t('system.task.belongProject')
      case 'module':
        return t('system.task.belongModule')
      default:
        return t('system.task.belongTopic')
    }
  }

  /** 搜索表单 → 接口参数（归属 type:id 多选拆解；多选数组用逗号连接，后端按 List 接收） */
  const buildSearchParams = (form: Record<string, any>): Record<string, any> => {
    const params: Record<string, any> = {}
    if (form.kw) params.kw = form.kw
    for (const key of ['status', 'priority', 'assignee', 'group']) {
      const value = form[key]
      if (Array.isArray(value)) {
        if (value.length) params[key] = value.join(',')
      } else if (value) {
        params[key] = value
      }
    }
    // 2026-09-10 按需求注释：子系统筛选去掉（如需恢复，取消下行注释）
    // if (form.subsystem) params.subsystemId = form.subsystem
    const belongs = Array.isArray(form.belong) ? form.belong : form.belong ? [form.belong] : []
    const grouped: Record<string, string[]> = { project: [], module: [], topic: [] }
    for (const item of belongs) {
      const [type, id] = String(item).split(':')
      if (id && grouped[type]) grouped[type].push(id)
    }
    if (grouped.project.length) params.projectId = grouped.project.join(',')
    if (grouped.module.length) params.moduleId = grouped.module.join(',')
    if (grouped.topic.length) params.topicId = grouped.topic.join(',')
    // 2026-09-10 用户需求：新增漏斗列的筛选统一走通用 filters 参数
    const filters = buildFilters(form, { 'hours': 'gte', 'planDate': 'between' })
    if (filters) params.filters = filters
    return params
  }

  const priorityTagType = (priority: string) => {
    switch (priority) {
      case '高':
        return 'danger'
      case '中':
        return 'warning'
      default:
        return 'info'
    }
  }

  const statusTagType = (status: string) => {
    switch (status) {
      case '已完成':
        return 'success'
      case '受阻':
        return 'danger'
      case '进行中':
        return 'primary'
      default:
        return 'info'
    }
  }

  const columnsFactory = () =>
    [
      // 2026-09-11 用户需求：批量删除需要行多选
      { type: 'selection', width: 50 },
      // 2026-09-10：查询条件内嵌至列头（useHeaderSlot），对应 #<prop>-header 插槽
      // 归属置首列：树形缩进与展开箭头落在归属列，与文案同行展示
      { prop: 'belongLabel', label: t('system.task.belong'), minWidth: 130, useHeaderSlot: true },
      { prop: 'title', label: t('system.task.title'), minWidth: 200, useSlot: true, useHeaderSlot: true },
      { prop: 'assignee', label: t('system.task.assignee'), minWidth: 100, useSlot: true, useHeaderSlot: true },
      { prop: 'priority', label: t('system.task.priority'), minWidth: 95, useSlot: true, useHeaderSlot: true },
      { prop: 'hours', label: t('system.task.hours'), minWidth: 90, useHeaderSlot: true },
      { prop: 'planDate', label: t('system.task.planDate'), minWidth: 105, useHeaderSlot: true },
      { prop: 'status', label: t('system.task.status'), minWidth: 100, useSlot: true, useHeaderSlot: true },
      { prop: 'group', label: t('system.task.group'), minWidth: 100, useHeaderSlot: true },
      {
        prop: 'operation',
        label: t('common.operation'),
        width: 165,
        fixed: 'right',
        useSlot: true
      }
    ] as ColumnOption<Api.Task.TaskListItem>[]

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
    const result = await fetchBatchDeleteTasks(selectedIds.value)
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
    refreshUpdate,
    refreshRemove
  } = useTable({
    core: { apiFn: fetchTaskPage, apiParams: { size: 50 } }
  })

  /** 查询：替换搜索条件并回到第一页 */
  const handleSearch = () => {
    replaceSearchParams(buildSearchParams(searchForm.value))
    fetchData()
  }

  /** 重置：清空条件（useTable 内部会自动重新请求） */
  const handleReset = () => {
    searchForm.value = {}
    resetSearchParams()
  }

  // ==================== 看板数据（与列表筛选相互独立，全量不分页） ====================

  const kanbanTasks = ref<Api.Task.TaskListItem[]>([])
  const kanbanLoading = ref(false)

  const loadKanban = async () => {
    kanbanLoading.value = true
    try {
      const page = await fetchTaskPage({ current: 1, size: 0 })
      kanbanTasks.value = page.records
    } finally {
      kanbanLoading.value = false
    }
  }

  watch(viewMode, (mode) => {
    if (mode === 'kanban') {
      loadKanban()
    }
  })

  // ==================== 操作 ====================

  /** 删除：明示级联子任务数量 */
  const handleDelete = async (row: Api.Task.TaskListItem) => {
    const tip =
      row.childCount > 0
        ? t('system.task.deleteCascadedTips', { count: row.childCount })
        : t('common.deleteTips')
    await ElMessageBox.confirm(tip, t('common.tips'), { type: 'warning' })
    const result = await fetchDeleteTask(row.id)
    if (result.cascaded > 0) {
      ElMessage.success(t('system.task.deleteCascadedSuccess', { count: result.cascaded }))
    } else {
      ElMessage.success(t('common.deleteSuccess'))
    }
    refreshRemove()
    if (viewMode.value === 'kanban') {
      loadKanban()
    }
  }

  const taskDialogRef = useTemplateRef<{
    open: (options: { row?: Api.Task.TaskListItem; parent?: Api.Task.TaskListItem }) => void
  }>('taskDialogRef')

  const openForm = (row?: Api.Task.TaskListItem) => {
    taskDialogRef.value?.open({ row })
  }

  /** 创建子任务（父任务归属自动继承） */
  const openSubtask = (row: Api.Task.TaskListItem) => {
    taskDialogRef.value?.open({ parent: row })
  }

  const handleDialogSuccess = () => {
    refreshUpdate()
    if (viewMode.value === 'kanban') {
      loadKanban()
    }
  }

  /** 看板拖拽写回（主维度字段，服务端二次校验；changed=false 静默） */
  const handleFieldChange = async (payload: {
    id: string
    field: string
    value: string
    display: string
  }) => {
    try {
      const result = await fetchPatchTask(payload.id, { field: payload.field, value: payload.value })
      if (result.changed !== false) {
        ElMessage.success(t('system.task.changedToast', { value: payload.display }))
        refreshUpdate()
      }
    } finally {
      // 成功按新值重新分列；失败/未变化恢复拖拽前的列状态
      loadKanban()
    }
  }

  /** 看板工时内联编辑提交 */
  const handleHoursCommit = async (payload: { id: string; value: number }) => {
    try {
      const result = await fetchPatchTask(payload.id, { field: 'hours', value: payload.value })
      if (result.changed !== false) {
        ElMessage.success(t('system.task.hoursUpdated'))
        refreshUpdate()
      }
    } finally {
      loadKanban()
    }
  }

  onMounted(async () => {
    taskOptions.value = await fetchTaskOptions()
    // 前端模式守卫不拉取真实用户信息：已登录时主动刷新一次，拿到后端 task:* 权限码（失败沿用现有 buttons）
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
