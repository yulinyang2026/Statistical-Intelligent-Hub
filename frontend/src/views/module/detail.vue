<!-- 模块详情（FR-MOD-007）：统计概览（含挂靠项目任务口径）+ 4 Tab（基础信息/进度看板/模块成员/文档附件） -->
<template>
  <div class="page-content">
    <!-- 头部：返回 + 模块名 + 状态 -->
    <div class="flex items-center gap-3">
      <ElButton circle v-ripple @click="goBack">
        <ArtSvgIcon icon="ri:arrow-left-line" />
      </ElButton>
      <h2 class="m-0 text-lg font-semibold">{{ detail?.name }}</h2>
      <ElTag v-if="detail" size="small" :type="statusTagType(detail.status)">{{ detail.status }}</ElTag>
      <!-- 2026-09-10 按需求注释：去掉「待办口径」提示文案
      <span class="text-xs text-g-500">{{ t('system.module.todoScopeTip') }}</span>
      -->
    </div>

    <!-- 统计概览（已完成任务/总数/完成率/文档数/待办，口径 = 直属任务 ∪ 挂靠项目任务） -->
    <div v-if="detail" class="mt-4 grid grid-cols-2 gap-3 md:grid-cols-5">
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.module.statTodo') }}</div>
        <div
          class="mt-1 text-2xl font-bold"
          :class="detail.todoCount >= 10 ? 'text-danger' : detail.todoCount > 0 ? 'text-warning' : 'text-g-400'"
        >
          {{ detail.todoCount }}
        </div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.module.statTotal') }}</div>
        <div class="mt-1 text-2xl font-bold">{{ detail.totalTaskCount }}</div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.module.statDone') }}</div>
        <div class="mt-1 text-2xl font-bold text-theme">{{ detail.doneTaskCount }}</div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.module.statRate') }}</div>
        <div class="mt-1 text-2xl font-bold text-theme">{{ detail.completionRate }}%</div>
      </div>
      <div class="art-card-sm p-4">
        <div class="text-xs text-g-500">{{ t('system.module.statDocs') }}</div>
        <div class="mt-1 text-2xl font-bold">{{ detail.docCount }}</div>
      </div>
    </div>

    <ElTabs v-model="activeTab" class="mt-4">
      <!-- ① 基础信息 -->
      <ElTabPane name="info" :label="t('system.module.tabInfo')">
        <ElDescriptions v-if="detail" :column="2" border class="mt-2">
          <ElDescriptionsItem :label="t('system.module.product')">{{ detail.product || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.module.category')">{{ detail.category || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.module.dept')">{{ detail.dept || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.module.team')">{{ detail.team || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.module.owner')">{{ detail.owner || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.module.rd')">{{ (detail.rd ?? []).join('、') || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.module.test')">{{ detail.test || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.module.cost')">{{ detail.cost || '—' }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.module.desc')" :span="2">{{ detail.desc || '—' }}</ElDescriptionsItem>
        </ElDescriptions>
      </ElTabPane>

      <!-- ② 进度看板（受限形态：状态四列 + 月份过滤 + 状态拖拽写回） -->
      <ElTabPane name="kanban" :label="t('system.module.tabKanban')">
        <ElAlert
          v-if="!canEditTask"
          type="warning"
          :closable="false"
          show-icon
          :title="t('system.task.readOnlyTip')"
          class="mb-2"
        />
        <KanbanView
          :tasks="moduleTasks"
          :loading="kanbanLoading"
          :readonly="!canEditTask"
          restricted
          @open-form="openTaskForm"
          @field-change="handleFieldChange"
          @hours-commit="handleHoursCommit"
        />
      </ElTabPane>

      <!-- ③ 模块成员（角色字段 + 任务执行人自动推导） -->
      <ElTabPane name="members" :label="t('system.module.tabMembers')">
        <ElEmpty v-if="!detail?.members?.length" :description="t('system.module.noMembers')" class="mt-6" />
        <ElTable v-else :data="detail.members" class="mt-2">
          <ElTableColumn :label="t('system.module.memberName')" min-width="140">
            <template #default="{ row }">
              <div class="flex items-center gap-1.5">
                <ElAvatar :size="24">{{ row.name?.charAt(0) }}</ElAvatar>
                <span>{{ row.name }}</span>
              </div>
            </template>
          </ElTableColumn>
          <ElTableColumn :label="t('system.module.memberRoles')" min-width="240">
            <template #default="{ row }">
              <ElTag v-for="role in row.roles" :key="role" size="small" class="mr-1.5">{{ role }}</ElTag>
            </template>
          </ElTableColumn>
        </ElTable>
      </ElTabPane>

      <!-- ④ 文档附件（文档中心同源，仅本模块文档） -->
      <ElTabPane name="docs" :label="t('system.module.tabDocs')">
        <div class="flex items-center justify-between">
          <span class="text-xs text-g-500">{{ t('system.module.docsTip') }}</span>
          <ElButton v-if="canEditDoc" type="primary" size="small" v-ripple @click="openDocForm()">
            <ArtSvgIcon icon="ri:add-line" class="mr-1" />
            {{ t('common.add') }}
          </ElButton>
        </div>
        <ElEmpty v-if="!docs.length" :description="t('system.module.noDocs')" class="mt-6" />
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
          <ElTableColumn :label="t('system.doc.summary')" min-width="220" show-overflow-tooltip>
            <template #default="{ row }">
              <span class="text-g-500">{{ row.summary }}</span>
            </template>
          </ElTableColumn>
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
           故本次直接删除、改由本行记录；恢复入口：后端详情接口仍返回 logs 字段、locales 仍保留 system.module.log* 文案 -->
    </ElTabs>

    <!-- 任务编辑（看板双击/点击卡片）与文档编辑 -->
    <TaskDialog ref="taskDialogRef" @success="loadKanban" />
    <DocDialog ref="docDialogRef" @success="refreshAfterDocChange" />
  </div>
</template>

<script setup lang="ts">
  import { fetchModuleDetail } from '@/api/module'
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

  defineOptions({ name: 'ModuleDetail' })

  const { t } = useI18n()
  const route = useRoute()
  const router = useRouter()
  const userStore = useUserStore()

  const moduleId = computed(() => route.params.id as string)
  const activeTab = ref<string>((route.query.tab as string) || 'info')

  const detail = ref<Api.Module.ModuleDetail>()

  // ==================== 权限（任务编辑 / 文档编辑，与任务页同款兜底逻辑） ====================

  const buttons = computed(() => userStore.info.buttons ?? [])
  const isSuper = computed(() => (userStore.info.roles ?? []).includes('R_SUPER'))
  const hasTaskCodes = computed(() => buttons.value.some((code) => code.startsWith('task:')))
  const canEditTask = computed(() => isSuper.value || !hasTaskCodes.value || buttons.value.includes('task:edit'))
  const hasDocCodes = computed(() => buttons.value.some((code) => code.startsWith('doc:')))
  const canEditDoc = computed(() => isSuper.value || !hasDocCodes.value || buttons.value.includes('doc:edit'))

  // ==================== 详情与统计 ====================

  const loadDetail = async () => {
    detail.value = await fetchModuleDetail(moduleId.value)
  }

  const goBack = () => {
    router.push('/module')
  }

  const statusTagType = (status: string) => {
    switch (status) {
      case '活跃':
        return 'success'
      default:
        return 'info'
    }
  }

  // ==================== 进度看板（模块任务全量，含挂靠项目任务） ====================

  const moduleTasks = ref<Api.Task.TaskListItem[]>([])
  const kanbanLoading = ref(false)

  const loadKanban = async () => {
    kanbanLoading.value = true
    try {
      const page = await fetchTaskPage({ current: 1, size: 0, moduleId: moduleId.value })
      moduleTasks.value = page.records
    } finally {
      kanbanLoading.value = false
    }
  }

  const taskDialogRef = useTemplateRef<{
    open: (options: { row?: Api.Task.TaskListItem; parent?: Api.Task.TaskListItem }) => void
  }>('taskDialogRef')

  const openTaskForm = (task?: Api.Task.TaskListItem) => {
    taskDialogRef.value?.open({ row: task })
  }

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
    const page = await fetchDocPage({ current: 1, size: 0, moduleId: moduleId.value })
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
        presetBelong: { type: 'module', id: detail.value.id, name: detail.value.name }
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
    if (tab === 'kanban' && !moduleTasks.value.length) {
      loadKanban()
    }
    if (tab === 'docs' && !docs.value.length) {
      loadDocs()
    }
  })

  watch(
    moduleId,
    () => {
      if (moduleId.value) {
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
