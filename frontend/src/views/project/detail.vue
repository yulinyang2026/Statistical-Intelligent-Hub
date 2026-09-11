<!-- 项目详情（FR-PROJ-008）：统计概览（**仅直属任务**口径）+ Tab（基础信息/子系统/进度看板/项目成员/文档附件）
     2026-09-11 用户需求：项目待办与子系统待办相互独立，概览不再含子系统任务 -->
<template>
  <div class="page-content">
    <!-- 头部：返回 + 项目名 + 级别/状态 -->
    <div class="flex items-center gap-3">
      <ElButton circle v-ripple @click="goBack">
        <ArtSvgIcon icon="ri:arrow-left-line" />
      </ElButton>
      <h2 class="m-0 text-lg font-semibold">{{ detail?.name }}</h2>
      <ElTag v-if="detail?.level" size="small" :type="levelTagType(detail.level)">{{ detail.level }}</ElTag>
      <ElTag v-if="detail" size="small" :type="statusTagType(detail.status)">{{ detail.status }}</ElTag>
      <!-- 2026-09-10 按需求注释：去掉「待办口径」提示文案
      <span class="text-xs text-g-500">{{ t('system.project.todoScopeTip') }}</span>
      -->
    </div>

    <!-- 统计概览（口径 = 项目下含各子系统任务，状态 ≠ 已完成 为待办） -->
    <div v-if="detail" class="mt-4 grid grid-cols-2 gap-3 md:grid-cols-6">
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.project.statTodo') }}</div>
        <div
          class="mt-1 text-2xl font-bold"
          :class="detail.todoCount >= 10 ? 'text-danger' : detail.todoCount > 0 ? 'text-warning' : 'text-g-400'"
        >
          {{ detail.todoCount }}
        </div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.project.statTotal') }}</div>
        <div class="mt-1 text-2xl font-bold">{{ detail.totalTaskCount }}</div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.project.statDone') }}</div>
        <div class="mt-1 text-2xl font-bold text-theme">{{ detail.doneTaskCount }}</div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.project.statRate') }}</div>
        <div class="mt-1 text-2xl font-bold text-theme">{{ detail.completionRate }}%</div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.project.statSubsystems') }}</div>
        <div class="mt-1 text-2xl font-bold">{{ detail.subsystems.length }}</div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.project.statDocs') }}</div>
        <div class="mt-1 text-2xl font-bold">{{ detail.docCount }}</div>
      </div>
    </div>

    <ElTabs v-model="activeTab" class="mt-4">
      <!-- ① 基础信息 -->
      <ElTabPane name="info" :label="t('system.project.tabInfo')">
        <ElDescriptions v-if="detail" :column="2" border class="mt-2">
          <ElDescriptionsItem :label="t('system.project.cost')">{{ detail.cost || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.level')">{{ detail.level || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.product')">{{ detail.product || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.productLine')">{{ detail.productLine || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.dept')">{{ detail.dept || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.team')">{{ detail.team || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.sale')">{{ detail.sale || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.pm')">{{ detail.pm || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.owner')">{{ detail.owner || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.rd')">{{ detail.rd || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.test')">{{ detail.test || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.status')">{{ detail.status || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.budget')">
            {{ detail.budget === null || detail.budget === undefined ? '—' : `${detail.budget} 万元` }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.dateRange')">
            {{ detail.startDate || '—' }} ~ {{ detail.endDate || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.stakeholders')">
            {{ (detail.stakeholders ?? []).join('、') || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.project.desc')" :span="2">{{ detail.desc || '—' }}</ElDescriptionsItem>
        </ElDescriptions>
      </ElTabPane>

      <!-- ② 子系统（维护 + 任务统计；待办口径与项目直属任务相互独立） -->
      <ElTabPane name="subsystems" :label="t('system.project.tabSubsystems')">
        <div class="flex items-center justify-end">
          <!-- 2026-09-11 用户需求：概览与子系统待办已相互独立，「未关联子系统的任务」提示随之下线（注释保留不删）
          <span class="text-xs text-g-500">
            {{ t('system.project.subsystemUnassigned', {
              tasks: detail?.unassignedTaskCount ?? 0,
              todo: detail?.unassignedTodoCount ?? 0
            }) }}
          </span>
          -->
          <ElButton v-if="canEdit" type="primary" size="small" v-ripple @click="openSubsystemForm()">
            <ArtSvgIcon icon="ri:add-line" class="mr-1" />
            {{ t('system.project.addSubsystem') }}
          </ElButton>
        </div>
        <ElEmpty v-if="!detail?.subsystems?.length" :description="t('system.project.noSubsystems')" class="mt-6" />
        <ElTable v-else :data="detail.subsystems" class="mt-2">
          <!-- 2026-09-11 用户需求：子系统字段与项目一致 -->
          <ElTableColumn :label="t('system.project.subsystemName')" min-width="160">
            <template #default="{ row }">
              <div class="flex items-center gap-1.5">
                <ArtSvgIcon icon="ri:node-tree" class="text-theme" />
                <span class="font-medium">{{ row.name }}</span>
              </div>
            </template>
          </ElTableColumn>
          <!-- 2026-09-11 用户需求：子系统成本对象字段下线，该列去掉
          <ElTableColumn :label="t('system.project.cost')" min-width="100" prop="cost" />
          -->
          <ElTableColumn :label="t('system.project.product')" min-width="100" prop="product" />
          <ElTableColumn :label="t('system.project.dept')" min-width="100" prop="dept" />
          <ElTableColumn :label="t('system.project.team')" min-width="100" prop="team" />
          <ElTableColumn :label="t('system.project.rd')" min-width="90" prop="rd" />
          <ElTableColumn :label="t('system.project.pm')" min-width="90" prop="pm" />
          <ElTableColumn :label="t('system.project.subsystemOwner')" min-width="90" prop="owner" />
          <ElTableColumn :label="t('system.project.test')" min-width="90" prop="test" />
          <ElTableColumn :label="t('system.project.status')" min-width="90">
            <template #default="{ row }">
              <ElTag v-if="row.status" size="small" :type="statusTagType(row.status)">{{ row.status }}</ElTag>
              <span v-else class="text-g-400">—</span>
            </template>
          </ElTableColumn>
          <ElTableColumn :label="t('system.project.todo')" min-width="85">
            <template #default="{ row }">
              <span v-if="row.todoCount > 0" class="font-bold text-warning">{{ row.todoCount }}</span>
              <span v-else class="text-g-400">0</span>
            </template>
          </ElTableColumn>
          <ElTableColumn :label="t('common.operation')" width="150" fixed="right">
            <template #default="{ row }">
              <div class="flex items-center">
                <!-- 2026-09-10 按需求：子系统不能再创建下级，去掉「添加」按钮（原为新增任务入口）
                <ArtButtonTable v-if="canCreateTask" type="add" icon="ri:add-line" @click="openSubsystemTask(row as Api.Project.SubsystemItem)" />
                -->
                <ArtButtonTable v-if="canEdit && row.canEdit" type="edit" @click="openSubsystemForm(row as Api.Project.SubsystemItem)" />
                <ArtButtonTable v-if="canEdit && row.canEdit" type="delete" @click="handleDeleteSubsystem(row as Api.Project.SubsystemItem)" />
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>

      <!-- ③ 进度看板（受限形态：状态四列 + 月份过滤 + 状态拖拽写回） -->
      <ElTabPane name="kanban" :label="t('system.project.tabKanban')">
        <ElAlert
          v-if="!canEditTask"
          type="warning"
          :closable="false"
          show-icon
          :title="t('system.task.readOnlyTip')"
          class="mb-2"
        />
        <KanbanView
          :tasks="projectTasks"
          :loading="kanbanLoading"
          :readonly="!canEditTask"
          restricted
          @open-form="openTaskForm"
          @field-change="handleFieldChange"
          @hours-commit="handleHoursCommit"
        />
      </ElTabPane>

      <!-- ④ 项目成员（角色字段 + 任务执行人自动推导） -->
      <ElTabPane name="members" :label="t('system.project.tabMembers')">
        <ElEmpty v-if="!detail?.members?.length" :description="t('system.project.noMembers')" class="mt-6" />
        <ElTable v-else :data="detail.members" class="mt-2">
          <ElTableColumn :label="t('system.project.memberName')" min-width="140">
            <template #default="{ row }">
              <div class="flex items-center gap-1.5">
                <ElAvatar :size="24">{{ row.name?.charAt(0) }}</ElAvatar>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn :label="t('system.project.memberRoles')" min-width="240">
            <template #default="{ row }">
              <ElTag v-for="role in row.roles" :key="role" size="small" class="mr-1.5">{{ role }}</ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>

      <!-- ⑤ 文档附件（文档中心同源，仅本项目文档） -->
      <ElTabPane name="docs" :label="t('system.project.tabDocs')">
        <div class="flex items-center justify-between">
          <span class="text-xs text-g-500">{{ t('system.project.docsTip') }}</span>
          <ElButton v-if="canEditDoc" type="primary" size="small" v-ripple @click="openDocForm()">
            <ArtSvgIcon icon="ri:add-line" class="mr-1" />
            {{ t('common.add') }}
          </ElButton>
        </div>
        <ElEmpty v-if="!docs.length" :description="t('system.project.noDocs')" class="mt-6" />
        <ElTable v-else :data="docs" class="mt-2">
          <ElTableColumn :label="t('system.doc.title')" min-width="200">
            <template #default="{ row }">
              <div class="flex items-center gap-1.5">
                <ArtSvgIcon icon="ri:file-mark-line" class="text-theme" />
                <span class="font-medium">{{ row.title }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn :label="t('system.doc.updatedBy')" min-width="100" prop="updatedBy" />
          <ElTableColumn :label="t('system.doc.updatedAt')" min-width="140" prop="updatedAt" />
          <ElTableColumn :label="t('system.doc.wordCount')" width="100" prop="wordCount" />
          <ElTableColumn :label="t('common.operation')" width="150" fixed="right">
            <template #default="{ row }">
              <div class="flex items-center">
                <ArtButtonTable type="view" @click="openDocForm(row as Api.Doc.DocListItem, true)" />
                <ArtButtonTable v-if="row.canEdit" type="edit" @click="openDocForm(row as Api.Doc.DocListItem)" />
                <ArtButtonTable v-if="row.canDelete" type="delete" @click="handleDeleteDoc(row as Api.Doc.DocListItem)" />
              </div>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>
      <!-- 2026-09-11 用户需求：详情页「操作日志」Tab 已移除（改由系统级操作日志页 /system/log 承载，仅管理员可见）。
           注意：该 Tab 曾用 HTML 注释包裹，因**内层注释的结束标记提前闭合了外层注释**而实际未生效（仍在渲染），
           故本次直接删除、改由本行记录；恢复入口：后端详情接口仍返回 logs 字段、locales 仍保留 system.project.log* 文案 -->
    </ElTabs>

    <!-- 子系统维护 / 任务编辑 / 文档编辑 -->
    <SubsystemDialog ref="subsystemDialogRef" @success="loadDetail" />
    <TaskDialog ref="taskDialogRef" @success="loadKanban" />
    <DocDialog ref="docDialogRef" @success="refreshAfterDocChange" />
  </div>
</template>

<script setup lang="ts">
  import { fetchDeleteSubsystem, fetchProjectDetail } from '@/api/project'
  import { fetchDeleteDoc, fetchDocPage } from '@/api/doc'
  import { fetchPatchTask, fetchTaskPage } from '@/api/task'
  import { fetchGetUserInfo } from '@/api/auth'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useRoute, useRouter } from 'vue-router'
  import { useUserStore } from '@/store/modules/user'
  import KanbanView from '@/views/task/modules/kanban-view.vue'
  import TaskDialog from '@/views/task/modules/task-dialog.vue'
  import DocDialog from '@/views/doc/modules/doc-dialog.vue'
  import SubsystemDialog from './modules/subsystem-dialog.vue'

  defineOptions({ name: 'ProjectDetail' })

  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()

  const projectId = computed(() => route.params.id as string)
  const activeTab = ref<string>((route.query.tab as string) || 'info')

  const detail = ref<Api.Project.ProjectDetail>()

  // ==================== 权限（项目编辑 / 任务编辑 / 文档编辑，与模块详情同款兜底逻辑） ====================

  const buttons = computed(() => userStore.info.buttons ?? [])
  const isSuper = computed(() => (userStore.info.roles ?? []).includes('R_SUPER'))
  const hasProjectCodes = computed(() => buttons.value.some((code) => code.startsWith('project:')))
  const canEdit = computed(
    () => isSuper.value || !hasProjectCodes.value || buttons.value.includes('project:edit')
  )
  const hasTaskCodes = computed(() => buttons.value.some((code) => code.startsWith('task:')))
  const canEditTask = computed(() => isSuper.value || !hasTaskCodes.value || buttons.value.includes('task:edit'))
  const canCreateTask = computed(
    () => isSuper.value || !hasTaskCodes.value || buttons.value.includes('task:create')
  )
  const hasDocCodes = computed(() => buttons.value.some((code) => code.startsWith('doc:')))
  const canEditDoc = computed(() => isSuper.value || !hasDocCodes.value || buttons.value.includes('doc:edit'))

  // ==================== 详情与统计 ====================

  const loadDetail = async () => {
    detail.value = await fetchProjectDetail(projectId.value)
  }

  const goBack = () => {
    router.push('/project')
  }

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

  // ==================== 进度看板（项目任务全量，含各子系统任务） ====================

  const projectTasks = ref<Api.Task.TaskListItem[]>([])
  const kanbanLoading = ref(false)

  const loadKanban = async () => {
    kanbanLoading.value = true
    try {
      const page = await fetchTaskPage({ current: 1, size: 0, projectId: projectId.value })
      projectTasks.value = page.records
    } finally {
      kanbanLoading.value = false
    }
  }

  const subsystemDialogRef = useTemplateRef<{
    open: (options?: {
      row?: Api.Project.SubsystemItem
      project?: { id: string; name: string }
      /** 新增时按所属项目预填（2026-09-11） */
      presetProject?: Api.Project.ProjectListItem | Api.Project.ProjectDetail
    }) => void
  }>('subsystemDialogRef')

  const openSubsystemForm = (row?: Api.Project.SubsystemItem) => {
    if (row) {
      subsystemDialogRef.value?.open({ row })
    } else if (detail.value) {
      subsystemDialogRef.value?.open({
        project: { id: detail.value.id, name: detail.value.name },
        presetProject: detail.value
      })
    }
  }

  const handleDeleteSubsystem = async (row: Api.Project.SubsystemItem) => {
    const tip =
      row.taskCount > 0
        ? t('system.project.subsystemDeleteTips', { tasks: row.taskCount })
        : t('common.deleteTips')
    await ElMessageBox.confirm(tip, t('common.tips'), { type: 'warning' })
    await fetchDeleteSubsystem(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    loadDetail()
  }

  const taskDialogRef = useTemplateRef<{
    open: (options: {
      row?: Api.Task.TaskListItem
      parent?: Api.Task.TaskListItem
      presetBelong?: { type: 'project' | 'module' | 'topic'; id: string }
      presetSubsystemId?: string
    }) => void
  }>('taskDialogRef')

  const openTaskForm = (task?: Api.Task.TaskListItem) => {
    taskDialogRef.value?.open({ row: task })
  }

  /* 2026-09-10 按需求注释：子系统「新增任务」入口去掉（子系统不能再创建下级）
  const openSubsystemTask = (row: Api.Project.SubsystemItem) => {
    taskDialogRef.value?.open({
      presetBelong: { type: 'project', id: row.projectId },
      presetSubsystemId: row.id
    })
  }
  */

  /** 看板拖拽写回（仅状态；changed=false 静默） */
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
        loadDetail()
      }
    } finally {
      loadKanban()
    }
  }

  const handleHoursCommit = async (payload: { id: string; value: number }) => {
    try {
      const result = await fetchPatchTask(payload.id, { field: 'hours', value: payload.value })
      if (result.changed !== false) {
        ElMessage.success(t('system.task.hoursUpdated'))
      }
    } finally {
      loadKanban()
    }
  }

  // ==================== 文档附件 ====================

  const docs = ref<Api.Doc.DocListItem[]>([])

  const loadDocs = async () => {
    const page = await fetchDocPage({ current: 1, size: 0, projectId: projectId.value })
    docs.value = page.records
  }

  const docDialogRef = useTemplateRef<{
    open: (options: {
      row?: Api.Doc.DocListItem
      view?: boolean
      presetBelong?: { type: string; id: string; name: string }
    }) => void
  }>('docDialogRef')

  const openDocForm = (row?: Api.Doc.DocListItem, view = false) => {
    if (row) {
      docDialogRef.value?.open({ row, view })
    } else if (detail.value) {
      docDialogRef.value?.open({
        presetBelong: { type: 'project', id: detail.value.id, name: detail.value.name }
      })
    }
  }

  const handleDeleteDoc = async (row: Api.Doc.DocListItem) => {
    await ElMessageBox.confirm(t('common.deleteTips'), t('common.tips'), { type: 'warning' })
    await fetchDeleteDoc(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    refreshAfterDocChange()
  }

  /** 文档变更：刷新文档列表与统计概览（文档数） */
  const refreshAfterDocChange = () => {
    loadDocs()
    loadDetail()
  }

  // ==================== Tab 懒加载 ====================

  watch(activeTab, (tab) => {
    if (tab === 'kanban' && !projectTasks.value.length) {
      loadKanban()
    }
    if (tab === 'docs' && !docs.value.length) {
      loadDocs()
    }
  })

  watch(
    projectId,
    () => {
      if (projectId.value) {
        loadDetail()
      }
    },
    { immediate: true }
  )

  onMounted(async () => {
    // 列表待办数字穿透默认落进度看板 Tab
    if (activeTab.value === 'kanban') {
      loadKanban()
    }
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
