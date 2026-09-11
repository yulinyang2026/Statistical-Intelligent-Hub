<!-- 任务新增 / 编辑弹窗（子任务继承父任务归属；父任务仅同归属可选） -->
<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="680px"
    :close-on-click-modal="false"
    append-to-body
    @closed="handleClosed"
  >
    <ArtForm
      ref="formRef"
      v-model="formData"
      :items="formItems"
      :rules="formRules"
      :show-reset="false"
      :show-submit="false"
    />
    <template #footer>
      <ElButton @click="visible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton type="primary" v-ripple @click="handleSubmit">
        {{ t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import { fetchCreateTask, fetchTaskOptions, fetchTaskPage, fetchUpdateTask } from '@/api/task'
  import { fetchUserOptions } from '@/api/user'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'TaskDialog' })

  /** 展平两级树（父 + 子） */
  const flattenTasks = (rows: Api.Task.TaskListItem[]): Api.Task.TaskListItem[] => {
    const result: Api.Task.TaskListItem[] = []
    for (const row of rows) {
      result.push(row)
      for (const child of row.children || []) {
        result.push(child)
      }
    }
    return result
  }

  /** 今天 YYYY-MM-DD */
  const currentDate = (): string => {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}-${String(now.getDate()).padStart(2, '0')}`
  }

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
  const editingId = ref<string>()
  /** 预设父任务（从列表「子任务」入口进入） */
  const presetParent = ref<Api.Task.TaskListItem>()
  const formData = ref<Record<string, any>>({})

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
  /** 执行人下拉（真实用户） */
  const assigneeOptions = ref<{ label: string; value: string }[]>([])
  /** 父任务候选（与当前归属一致、排除自身及子孙） */
  const parentOptions = ref<{ label: string; value: string }[]>([])
  /** 全部任务（父任务候选计算用） */
  const allTasks = ref<Api.Task.TaskListItem[]>([])

  const dialogTitle = computed(() => (isEdit.value ? t('common.edit') : t('common.add')))

  const belongLabelMap = computed(() => ({
    project: t('system.task.belongProject'),
    module: t('system.task.belongModule'),
    topic: t('system.task.belongTopic')
  }))

  /** 归属选项（无归属 + 项目/模块/专题） */
  const belongOptions = computed(() => [
    { label: t('system.task.belongNone'), value: '' },
    ...taskOptions.value.belongOptions.map((option) => ({
      label: `${belongLabelMap.value[option.type]}·${option.name}`,
      value: `${option.type}:${option.id}`
    }))
  ])

  /** 当前归属是否项目（决定子系统字段显隐与候选项，FR-PROJ-014） */
  const belongProjectId = computed(() => {
    const belong = formData.value.belong as string | undefined
    return belong?.startsWith('project:') ? belong.slice(8) : ''
  })

  /** 子系统候选：归属为项目时，仅该项目下的子系统 */
  const subsystemOptions = computed(() =>
    taskOptions.value.subsystems
      .filter((item) => item.projectId === belongProjectId.value)
      .map((item) => ({ label: item.name, value: item.id }))
  )

  const formItems = computed(() => [
    {
      key: 'title',
      label: t('system.task.title'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.task.titlePlaceholder'), maxlength: 128 }
    },
    {
      key: 'parent',
      label: t('system.task.parent'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.task.parentPlaceholder'),
        clearable: true,
        filterable: true,
        disabled: !!presetParent.value,
        options: parentOptions.value
      }
    },
    {
      key: 'belong',
      label: t('system.task.belong'),
      type: 'select',
      span: 12,
      hidden: !!presetParent.value || !!formData.value.parent,
      props: { placeholder: t('system.task.belongPlaceholder'), clearable: true, options: belongOptions.value }
    },
    {
      key: 'subsystem',
      label: t('system.task.subsystem'),
      type: 'select',
      span: 12,
      hidden: !belongProjectId.value || !!presetParent.value,
      props: {
        placeholder: t('system.task.subsystemPlaceholder'),
        clearable: true,
        options: subsystemOptions.value
      }
    },
    {
      key: 'assignee',
      label: t('system.task.assignee'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.task.assigneePlaceholder'),
        clearable: true,
        filterable: true,
        options: assigneeOptions.value
      }
    },
    {
      key: 'priority',
      label: t('system.task.priority'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.task.priorityPlaceholder'),
        options: [
          { label: t('system.task.priorityHigh'), value: '高' },
          { label: t('system.task.priorityMedium'), value: '中' },
          { label: t('system.task.priorityLow'), value: '低' }
        ]
      }
    },
    {
      key: 'planDate',
      label: t('system.task.planDate'),
      type: 'date',
      span: 12,
      props: {
        placeholder: t('system.task.planDatePlaceholder'),
        type: 'date',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD'
      }
    },
    {
      key: 'hours',
      label: t('system.task.hours'),
      type: 'number',
      span: 12,
      props: { min: 0, max: 9999 }
    },
    {
      key: 'status',
      label: t('system.task.status'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.task.statusPlaceholder'),
        options: [
          { label: t('system.task.statusTodo'), value: '待处理' },
          { label: t('system.task.statusDoing'), value: '进行中' },
          { label: t('system.task.statusDone'), value: '已完成' },
          { label: t('system.task.statusBlocked'), value: '受阻' }
        ]
      }
    },
    {
      key: 'group',
      label: t('system.task.group'),
      type: 'select',
      span: 12,
      props: {
        // 2026-09-11 用户需求：归属小组必选，且候选来自组织管理（不再允许自由输入）
        placeholder: t('system.task.groupPlaceholder'),
        clearable: true,
        filterable: true,
        options: taskOptions.value.groups.map((group) => ({ label: group, value: group }))
      }
    },
    {
      key: 'desc',
      label: t('system.task.desc'),
      type: 'input',
      span: 24,
      props: { placeholder: t('system.task.descPlaceholder'), maxlength: 500, type: 'textarea', rows: 3 }
    }
  ])

  /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      title: [{ required: true, message: t('system.task.titlePlaceholder') }],
      assignee: [{ required: true, message: t('system.task.assigneePlaceholder') }],
      planDate: [{ required: true, message: t('system.task.planDatePlaceholder') }],
      status: [{ required: true, message: t('system.task.statusPlaceholder') }],
      group: [{ required: true, message: t('system.task.groupPlaceholder') }]
    }
    // 2026-09-11 用户需求：归属必选（子任务继承父任务归属，故仅顶层任务要求）
    if (!presetParent.value && !formData.value.parent) {
      rules.belong = [{ required: true, message: t('system.task.belongPlaceholder') }]
    }
    return rules
  })
  const open = async (options: {
    row?: Api.Task.TaskListItem
    parent?: Api.Task.TaskListItem
    /** 预置归属（子系统行「新增任务」等入口用） */
    presetBelong?: { type: 'project' | 'module' | 'topic'; id: string }
    /** 预置子系统（FR-PROJ-014） */
    presetSubsystemId?: string
  }) => {
    const row = options?.row
    presetParent.value = options?.parent
    isEdit.value = !!row
    editingId.value = row?.id
    const presetBelong = options?.presetBelong
    const presetSubsystemId = options?.presetSubsystemId

    const [opts, users, taskPage] = await Promise.all([
      fetchTaskOptions(),
      fetchUserOptions(),
      fetchTaskPage({ current: 1, size: 1000 })
    ])
    taskOptions.value = opts
    assigneeOptions.value = users.map((user) => ({ label: user.name, value: user.name }))
    allTasks.value = flattenTasks(taskPage.records)

    if (row) {
      const belong =
        row.projectId !== null
          ? `project:${row.projectId}`
          : row.moduleId !== null
            ? `module:${row.moduleId}`
            : row.topicId !== null
              ? `topic:${row.topicId}`
              : ''
      formData.value = {
        title: row.title,
        desc: row.desc,
        parent: row.parentId ?? undefined,
        belong,
        subsystem: row.subsystemId ?? undefined,
        assignee: row.assignee,
        priority: row.priority,
        planDate: row.planDate,
        hours: row.hours,
        status: row.status,
        group: row.group
      }
    } else if (presetParent.value) {
      formData.value = {
        parent: presetParent.value.id,
        assignee: '',
        priority: '中',
        planDate: presetParent.value.planDate || currentDate(),
        status: '待处理',
        group: presetParent.value.group
      }
    } else {
      formData.value = {
        belong: presetBelong ? `${presetBelong.type}:${presetBelong.id}` : '',
        subsystem: presetSubsystemId,
        priority: '中',
        planDate: currentDate(),
        status: '待处理'
      }
    }
    updateParentOptions()
    visible.value = true
  }

  /** 父任务候选：与当前归属一致的根任务，排除自身及子孙 */
  const updateParentOptions = () => {
    const belong = formData.value.belong as string | undefined
    const parent = formData.value.parent as string | undefined
    const excluded = new Set<string>()
    if (editingId.value) {
      excluded.add(editingId.value)
      const self = allTasks.value.find((t) => t.id === editingId.value)
      if (self) {
        for (const child of self.children || []) {
          excluded.add(child.id)
        }
      }
    }
    parentOptions.value = allTasks.value
      .filter((t) => !t.parentId)
      .filter((t) => {
        if (!belong) return !t.projectId && !t.moduleId && !t.topicId
        const [type, id] = belong.split(':')
        if (type === 'project') return t.projectId === id
        if (type === 'module') return t.moduleId === id
        return t.topicId === id
      })
      .filter((t) => !excluded.has(t.id))
      .filter((t) => t.id !== parent)
      .map((t) => ({ label: t.title, value: t.id }))
  }

  /** 归属切换：离开项目时清空子系统（子系统仅归属项目可选，FR-PROJ-014） */
  watch(
    () => belongProjectId.value,
    (projectId) => {
      if (!projectId) {
        formData.value.subsystem = undefined
      } else if (
        formData.value.subsystem &&
        !subsystemOptions.value.some((option) => option.value === formData.value.subsystem)
      ) {
        // 切换到其它项目时，原子系统不属于新项目则清空
        formData.value.subsystem = undefined
      }
    }
  )

  const handleClosed = () => {
    formData.value = {}
    presetParent.value = undefined
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    const { title, assignee, planDate, status } = formData.value
    if (!title?.trim()) {
      ElMessage.warning(t('system.task.titlePlaceholder'))
      return
    }
    if (!assignee) {
      ElMessage.warning(t('system.task.assigneePlaceholder'))
      return
    }
    if (!planDate) {
      ElMessage.warning(t('system.task.planDatePlaceholder'))
      return
    }
    if (!status) {
      ElMessage.warning(t('system.task.statusPlaceholder'))
      return
    }
    // 2026-09-11 用户需求：归属与归属小组必选（子任务继承父任务归属，故仅顶层任务校验归属）
    const isChildTask = !!(presetParent.value?.id ?? formData.value.parent)
    if (!isChildTask && !formData.value.belong) {
      ElMessage.warning(t('system.task.belongPlaceholder'))
      return
    }
    if (!formData.value.group) {
      ElMessage.warning(t('system.task.groupPlaceholder'))
      return
    }
    const parent = presetParent.value?.id ?? formData.value.parent ?? null
    const belong = formData.value.belong as string | undefined
    const payload: Api.Task.TaskSaveParams = {
      id: editingId.value,
      title: title.trim(),
      desc: formData.value.desc,
      projectId: belong?.startsWith('project:') ? belong.slice(8) : null,
      moduleId: belong?.startsWith('module:') ? belong.slice(7) : null,
      topicId: belong?.startsWith('topic:') ? belong.slice(6) : null,
      subsystemId: belong?.startsWith('project:') ? (formData.value.subsystem ?? null) : null,
      assignee,
      priority: formData.value.priority ?? '中',
      hours: formData.value.hours ?? null,
      week: null,
      planDate,
      status,
      group: formData.value.group,
      parentId: parent || null
    }
    if (isEdit.value && editingId.value) {
      await fetchUpdateTask(editingId.value, payload)
    } else {
      await fetchCreateTask(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
