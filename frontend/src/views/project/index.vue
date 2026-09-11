<!-- 项目管理：列表树形（项目 → 子系统，FR-PROJ-001~007/FR-V5-017：筛选选定即查、待办穿透详情、子系统同权操作、Excel 导入导出） -->
<template>
  <div class="page-content flex flex-col" style="height: var(--art-full-height)">
    <!-- 只读提示条（无 project:edit 权限） -->
    <ElAlert
      v-if="!canEdit"
      type="warning"
      :closable="false"
      show-icon
      :title="t('system.project.readOnlyTip')"
      class="mb-4 shrink-0"
    />

    <!-- 2026-09-10 按需求注释：查询控件改为表头漏斗筛选（列 useHeaderSlot + #<prop>-header 插槽），
         查询条件与联动逻辑不变（searchForm / buildSearchParams / handleSearch 复用），原查询组件代码保留备查
    <ArtSearchBar
      v-model="searchForm"
      :items="searchItems"
      class="mt-2"
      @search="handleSearch"
      @reset="handleReset"
    />
    -->

    <!-- 2026-09-10 按需求：全部工具按钮与表格工具栏同一行（#left 插槽） -->
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
            {{ t('system.project.addProject') }}
          </ElButton>
          <ElButton v-if="canEdit" v-ripple @click="openSubsystemForm()">
            <ArtSvgIcon icon="ri:node-tree" class="mr-1" />
            {{ t('system.project.addSubsystem') }}
          </ElButton>
          <!-- 导入需编辑权限；导出仅查看权限即可（project:view） -->
          <ElButton v-if="canEdit" v-ripple @click="triggerImport">
            <ArtSvgIcon icon="ri:upload-2-line" class="mr-1" />
            {{ t('system.project.importButton') }}
          </ElButton>
          <ElButton v-ripple @click="handleExport">
            <ArtSvgIcon icon="ri:download-2-line" class="mr-1" />
            {{ t('system.project.exportButton') }}
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
          <!-- 2026-09-10 按需求注释：去掉「待办口径」提示文案
          <span class="ml-3 text-xs text-g-500">{{ t('system.project.todoScopeTip') }}</span>
          -->
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
      <!-- 表头筛选（2026-09-10 用户需求：所有字段均带漏斗） -->
      <template #name-header>
        <HeaderFilter
          :label="t('system.project.title')"
          v-model="searchForm.kw"
          :placeholder="t('system.project.searchPlaceholder')"
          @change="handleSearch"
        />
      </template>

      <template #cost-header>
        <HeaderFilter
          :label="t('system.project.cost')"
          v-model="searchForm.cost"
          :placeholder="t('system.project.cost')"
          @change="handleSearch"
        />
      </template>

      <template #level-header>
        <HeaderFilter
          :label="t('system.project.level')"
          v-model="searchForm.level"
          :options="toOptions(projectOptions.levels)"
          @change="handleSearch"
        />
      </template>

      <template #product-header>
        <HeaderFilter
          :label="t('system.project.product')"
          v-model="searchForm.product"
          :options="toOptions(projectOptions.products)"
          @change="handleSearch"
        />
      </template>

      <template #dept-header>
        <HeaderFilter
          :label="t('system.project.dept')"
          v-model="searchForm.dept"
          :options="toOptions(projectOptions.depts)"
          @change="handleSearch"
        />
      </template>

      <template #team-header>
        <HeaderFilter
          :label="t('system.project.team')"
          v-model="searchForm.team"
          :options="toOptions(projectOptions.teams)"
          @change="handleSearch"
        />
      </template>

      <template #rd-header>
        <HeaderFilter
          :label="t('system.project.rd')"
          v-model="searchForm.rd"
          :placeholder="t('system.project.rd')"
          @change="handleSearch"
        />
      </template>

      <template #owner-header>
        <HeaderFilter
          :label="t('system.project.owner')"
          v-model="searchForm.owner"
          :options="toOptions(projectOptions.owners)"
          @change="handleSearch"
        />
      </template>

      <template #test-header>
        <HeaderFilter
          :label="t('system.project.test')"
          v-model="searchForm.test"
          :placeholder="t('system.project.test')"
          @change="handleSearch"
        />
      </template>

      <template #status-header>
        <HeaderFilter
          :label="t('system.project.status')"
          v-model="searchForm.status"
          :options="toOptions(projectOptions.statuses)"
          @change="handleSearch"
        />
      </template>

      <template #productLine-header>
        <HeaderFilter
          :label="t('system.project.productLine')"
          v-model="searchForm.productLine"
          :placeholder="t('system.project.productLine')"
          @change="handleSearch"
        />
      </template>

      <template #pm-header>
        <HeaderFilter
          :label="t('system.project.pm')"
          v-model="searchForm.pm"
          :options="toOptions(projectOptions.owners)"
          @change="handleSearch"
        />
      </template>

      <template #sale-header>
        <HeaderFilter
          :label="t('system.project.sale')"
          v-model="searchForm.sale"
          :options="toOptions(projectOptions.owners)"
          @change="handleSearch"
        />
      </template>

      <template #startDate-header>
        <HeaderFilter
          :label="t('system.project.dateRange')"
          v-model="searchForm.startDate"
          mode="daterange"
          @change="handleSearch"
        />
      </template>

      <template #budget-header>
        <HeaderFilter
          :label="t('system.project.budget')"
          v-model="searchForm.budget"
          mode="number"
          :placeholder="t('system.project.budget')"
          @change="handleSearch"
        />
      </template>

      <template #stakeholders-header>
        <HeaderFilter
          :label="t('system.project.stakeholders')"
          v-model="searchForm.stakeholders"
          :placeholder="t('system.project.stakeholders')"
          @change="handleSearch"
        />
      </template>

      <template #desc-header>
        <HeaderFilter
          :label="t('system.project.desc')"
          v-model="searchForm.desc"
          :placeholder="t('system.project.desc')"
          @change="handleSearch"
        />
      </template>

      <template #todoCount-header>
        <HeaderFilter
          :label="t('system.project.todo')"
          v-model="searchForm.todoCount"
          mode="number"
          :placeholder="t('system.project.todo')"
          @change="handleSearch"
        />
      </template>
        <!-- 项目 / 子系统名称：项目行点击进入详情；副行显示挂靠与子系统数
             2026-09-10 按需求：首行用 inline-flex，树形展开箭头与文案保持同一行（不换行） -->
        <template #name="{ row }">
          <span class="inline-flex items-center gap-1.5 align-middle">
            <ElTag v-if="row.nodeType === 'subsystem'" size="small" type="info">
              {{ t('system.project.subsystemBadge') }}
            </ElTag>
            <ElLink type="primary" :underline="false" @click="goDetail(row)">
              <span class="font-medium">{{ row.name }}</span>
            </ElLink>
          </span>
        <!-- 2026-09-11 用户需求：挂靠概念移除，项目行副行只显示子系统数 -->
        <div v-if="row.nodeType === 'project' && row.subsystemCount" class="mt-0.5 text-xs text-g-500">
          {{ t('system.project.subsystemCount', { n: row.subsystemCount }) }}
        </div>
        </template>

        <!-- 成本对象：仅项目行有值（2026-09-11 用户需求：子系统成本对象字段下线，子系统行留空） -->
        <template #cost="{ row }">
          <span v-if="row.nodeType === 'project'" class="text-g-600">{{ row.cost || '—' }}</span>
        </template>

        <!-- 重要级别 -->
        <template #level="{ row }">
          <ElTag v-if="row.level" size="small" :type="levelTagType(row.level)">{{ row.level }}</ElTag>
          <span v-else class="text-g-400">—</span>
        </template>

        <!-- 项目状态（人工维护，不自动流转） -->
        <template #status="{ row }">
          <ElTag size="small" :type="statusTagType(row.status)">{{ row.status || '—' }}</ElTag>
        </template>

        <!-- 项目周期（开始 ~ 结束） -->
        <template #startDate="{ row }">
          <span class="text-g-600">{{ row.startDate || '—' }} ~ {{ row.endDate || '—' }}</span>
        </template>

        <!-- 干系人 -->
        <template #stakeholders="{ row }">
          <span class="text-g-600">{{ (row.stakeholders ?? []).join('、') || '—' }}</span>
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

        <!-- 操作列：项目行 查看/编辑/删除；子系统行 查看/编辑/删除
             2026-09-10 按需求：子系统不能再创建下级，表格中子系统行去掉「添加」按钮
             （原按钮实为「新增任务」，改由任务管理按归属项目 + 子系统创建）
        -->
        <template #operation="{ row }">
          <div class="flex items-center">
            <template v-if="row.nodeType === 'subsystem'">
              <ArtButtonTable type="view" @click="goDetail(row, 'subsystems')" />
              <ArtButtonTable v-if="canEdit && row.canEdit" type="edit" @click="openSubsystemForm(row)" />
              <ArtButtonTable v-if="canEdit && row.canEdit" type="delete" @click="handleDeleteSubsystem(row)" />
            </template>
            <template v-else>
              <ArtButtonTable type="view" @click="goDetail(row)" />
              <ArtButtonTable v-if="canEdit && row.canEdit" type="edit" @click="openForm(row)" />
              <ArtButtonTable v-if="canEdit && row.canEdit" type="delete" @click="handleDelete(row)" />
            </template>
          </div>
        </template>
      </ArtTable>

    </div>

    <!-- 导入文件选择（隐藏） -->
    <input
      ref="importInputRef"
      type="file"
      accept=".xlsx"
      class="hidden"
      @change="handleImportFile"
    />

    <ProjectDialog ref="projectDialogRef" @success="refreshUpdate" />
    <SubsystemDialog ref="subsystemDialogRef" @success="refreshUpdate" />
    <!-- 2026-09-10 按需求注释：子系统行不再提供「新增任务」入口，列表页不再挂载任务弹窗
    <TaskDialog ref="taskDialogRef" @success="refreshUpdate" />
    -->
  </div>
</template>

<script setup lang="ts">
  import {
    fetchDeleteProject,
    fetchDeleteSubsystem,
    fetchImportProject,
    fetchProjectDetail,
    fetchProjectOptions,
    fetchProjectPage,
    projectExportUrl,
    fetchBatchDeleteProjects
} from '@/api/project'
  import { fetchGetUserInfo } from '@/api/auth'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { useRouter } from 'vue-router'
  import { useUserStore } from '@/store/modules/user'
  import type { ColumnOption } from '@/types'
  import ProjectDialog from './modules/project-dialog.vue'
  import SubsystemDialog from './modules/subsystem-dialog.vue'
  import TaskDialog from '@/views/task/modules/task-dialog.vue'
  import HeaderFilter from '@/components/business/header-filter/index.vue'
  import { buildFilters } from '@/components/business/header-filter/filters'

  defineOptions({ name: 'ProjectIndex' })

  const { t } = useI18n()
  const router = useRouter()
  const userStore = useUserStore()

  /** 权限点判定（与模块/任务页同款：admin 全量放行 + 前端模式 mock 兜底） */
  const buttons = computed(() => userStore.info.buttons ?? [])
  const isSuper = computed(() => (userStore.info.roles ?? []).includes('R_SUPER'))
  const hasProjectCodes = computed(() => buttons.value.some((code) => code.startsWith('project:')))
  const canEdit = computed(
    () => isSuper.value || !hasProjectCodes.value || buttons.value.includes('project:edit')
  )
  const hasTaskCodes = computed(() => buttons.value.some((code) => code.startsWith('task:')))
  const canCreateTask = computed(
    () => isSuper.value || !hasTaskCodes.value || buttons.value.includes('task:create')
  )

  /** 搜索表单（与 useTable 的 searchParams 解耦，避免分页字段被清空） */
  const searchForm = ref<Record<string, any>>({})

  /** 筛选候选值 */
  const projectOptions = ref<Api.Project.ProjectOptions>({
    products: [],
    statuses: [],
    levels: [],
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
        label: t('system.project.title'),
        type: 'input',
        props: { placeholder: t('system.project.searchPlaceholder'), clearable: true }
      },
      {
        key: 'status',
        label: t('system.project.status'),
        type: 'select',
        props: {
          placeholder: t('system.project.statusPlaceholder'),
          clearable: true,
          options: opts(projectOptions.value.statuses)
        }
      },
      {
        key: 'product',
        label: t('system.project.product'),
        type: 'select',
        props: {
          placeholder: t('system.project.productPlaceholder'),
          clearable: true,
          options: opts(projectOptions.value.products)
        }
      },
      {
        key: 'owner',
        label: t('system.project.owner'),
        type: 'select',
        props: {
          placeholder: t('system.project.ownerPlaceholder'),
          clearable: true,
          filterable: true,
          options: opts(projectOptions.value.owners)
        }
      },
      {
        key: 'level',
        label: t('system.project.level'),
        type: 'select',
        props: {
          placeholder: t('system.project.levelPlaceholder'),
          clearable: true,
          options: opts(projectOptions.value.levels)
        }
      }
    ]
  })

  const levelTagType = (level: string) => {
    switch (level) {
      case 'A':
        return 'danger'
      case 'B':
        return 'warning'
      case 'C':
        return 'primary'
      default:
        return 'info'
    }
  }

  const statusTagType = (status: string) => {
    switch (status) {
      case '在建':
        return 'primary'
      case '已签约':
        return 'warning'
      case '完成':
        return 'success'
      default:
        return 'info'
    }
  }

  const columnsFactory = () =>
    [
      // 2026-09-11 用户需求：批量删除需要行多选
      { type: 'selection', width: 50 },
      // 2026-09-11 用户需求：默认展示 项目名称/成本对象/重要级别/归属产品/负责部门/负责小组/研发负责人/项目经理/项目状态/待办任务/操作；
      // 其余列 visible:false（可通过表头「列选」开启）；列宽 minWidth 弹性自适应、字段间均匀排布
      { prop: 'name', label: t('system.project.title'), minWidth: 150, useSlot: true, useHeaderSlot: true },
      // 2026-09-11 用户需求：成本对象仅项目行展示（子系统成本对象字段下线，子系统行不展示）
      { prop: 'cost', label: t('system.project.cost'), minWidth: 95, useSlot: true, useHeaderSlot: true },
      { prop: 'level', label: t('system.project.level'), minWidth: 80, useSlot: true, useHeaderSlot: true },
      { prop: 'product', label: t('system.project.product'), minWidth: 95, useHeaderSlot: true },
      { prop: 'productLine', label: t('system.project.productLine'), minWidth: 105, visible: false, useHeaderSlot: true },
      { prop: 'dept', label: t('system.project.dept'), minWidth: 95, useHeaderSlot: true },
      { prop: 'team', label: t('system.project.team'), minWidth: 95, useHeaderSlot: true },
      { prop: 'rd', label: t('system.project.rd'), minWidth: 90, useHeaderSlot: true },
      { prop: 'pm', label: t('system.project.pm'), minWidth: 95, useHeaderSlot: true },
      { prop: 'owner', label: t('system.project.owner'), minWidth: 95, visible: false, useHeaderSlot: true },
      { prop: 'sale', label: t('system.project.sale'), minWidth: 95, visible: false, useHeaderSlot: true },
      { prop: 'test', label: t('system.project.test'), minWidth: 90, visible: false, useHeaderSlot: true },
      { prop: 'status', label: t('system.project.status'), minWidth: 90, useSlot: true, useHeaderSlot: true },
      { prop: 'startDate', label: t('system.project.dateRange'), minWidth: 160, visible: false, useSlot: true, useHeaderSlot: true },
      { prop: 'budget', label: t('system.project.budget'), minWidth: 95, visible: false, useHeaderSlot: true },
      { prop: 'stakeholders', label: t('system.project.stakeholders'), minWidth: 120, visible: false, useSlot: true, useHeaderSlot: true },
      { prop: 'desc', label: t('system.project.desc'), minWidth: 150, visible: false, useHeaderSlot: true },
      { prop: 'todoCount', label: t('system.project.todo'), minWidth: 85, useSlot: true, useHeaderSlot: true },
      {
        prop: 'operation',
        label: t('common.operation'),
        width: 165,
        fixed: 'right',
        useSlot: true
      }
    ] as ColumnOption<Api.Project.ProjectListItem>[]

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
    const result = await fetchBatchDeleteProjects(selectedIds.value)
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
    core: { apiFn: fetchProjectPage, apiParams: { size: 50 } }
  })

  /** 查询：替换搜索条件并回到第一页（ArtSearchBar 选定即触发） */
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
    for (const key of ['kw', 'status', 'product', 'owner', 'level']) {
      const value = form[key]
      if (Array.isArray(value)) {
        if (value.length) params[key] = value.join(',')
      } else if (value) {
        params[key] = value
      }
    }
    // 2026-09-10 用户需求：新增漏斗列的筛选统一走通用 filters 参数
    const filters = buildFilters(form, {
      cost: 'like',
      productLine: 'like',
      dept: 'in',
      team: 'in',
      rd: 'like',
      pm: 'like',
      sale: 'like',
      test: 'like',
      startDate: 'between',
      budget: 'gte',
      stakeholders: 'like',
      desc: 'like',
      todoCount: 'gte'
    })
    if (filters) params.filters = filters
    return params
  }

  // ==================== 操作 ====================

  /** 项目行 → 项目详情；子系统行 → 所属项目详情（默认落「子系统」Tab） */
  const goDetail = (row: Api.Project.ProjectListItem | Api.Project.SubsystemItem, tab?: string) => {
    const id = row.nodeType === 'subsystem' ? row.projectId : row.id
    const targetTab = tab ?? (row.nodeType === 'subsystem' ? 'subsystems' : undefined)
    router.push({ path: `/project/detail/${id}`, query: targetTab ? { tab: targetTab } : undefined })
  }

  const projectDialogRef = useTemplateRef<{ open: (row?: Api.Project.ProjectListItem) => void }>(
    'projectDialogRef'
  )
  const subsystemDialogRef = useTemplateRef<{
    open: (options?: {
      row?: Api.Project.SubsystemItem
      project?: { id: string; name: string }
      /** 新增时按所属项目预填（2026-09-11） */
      presetProject?: Api.Project.ProjectListItem | Api.Project.ProjectDetail
    }) => void
  }>('subsystemDialogRef')
  /* 2026-09-10 按需求注释：子系统行去掉「添加」按钮后不再需要任务弹窗入口（任务改由任务管理创建）
  const taskDialogRef = useTemplateRef<{
    open: (options: {
      row?: Api.Task.TaskListItem
      parent?: Api.Task.TaskListItem
      presetBelong?: { type: 'project' | 'module' | 'topic'; id: string }
      presetSubsystemId?: string
    }) => void
  }>('taskDialogRef')
  */

  const openForm = (row?: Api.Project.ProjectListItem) => {
    projectDialogRef.value?.open(row)
  }

  const openSubsystemForm = (row?: Api.Project.SubsystemItem, project?: Api.Project.ProjectListItem) => {
    if (row) {
      subsystemDialogRef.value?.open({ row })
      return
    }
    // 列表页「新增子系统」：弹窗内先选所属项目；带项目进入时按项目预填同名字段
    subsystemDialogRef.value?.open(project ? { presetProject: project } : undefined)
  }

  /* 2026-09-10 按需求注释：子系统行「新增任务」入口去掉（子系统不能再创建下级）
  const openSubsystemTask = (row: Api.Project.SubsystemItem) => {
    taskDialogRef.value?.open({
      presetBelong: { type: 'project', id: row.projectId },
      presetSubsystemId: row.id
    })
  }
  */

  /** 删除项目：二次确认，明示将级联删除的子系统数、任务数与文档数（先取详情统计） */
  const handleDelete = async (row: Api.Project.ProjectListItem) => {
    const detail = await fetchProjectDetail(row.id)
    const tip = t('system.project.deleteCascadedTips', {
      subsystems: detail.subsystems.length,
      tasks: detail.totalTaskCount,
      docs: detail.docCount
    })
    await ElMessageBox.confirm(tip, t('common.tips'), { type: 'warning' })
    await fetchDeleteProject(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    refreshUpdate()
  }

  /** 删除子系统：任务不删除仅解除关联，确认框明示受影响任务数 */
  const handleDeleteSubsystem = async (row: Api.Project.SubsystemItem) => {
    const tip =
      row.taskCount > 0
        ? t('system.project.subsystemDeleteTips', { tasks: row.taskCount })
        : t('common.deleteTips')
    await ElMessageBox.confirm(tip, t('common.tips'), { type: 'warning' })
    await fetchDeleteSubsystem(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    refreshUpdate()
  }

  /** 导出：当前筛选结果全量（UTF-8 BOM，Excel 打开中文不乱码；token 走 ?token=） */
  const handleExport = () => {
    const url = projectExportUrl(buildSearchParams(searchForm.value), userStore.accessToken ?? '')
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
    const result = await fetchImportProject(file)
    ElMessage.success(
      t('system.project.importResult', { created: result.created, skipped: result.skipped })
    )
    refreshUpdate()
  }

  onMounted(async () => {
    projectOptions.value = await fetchProjectOptions()
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
