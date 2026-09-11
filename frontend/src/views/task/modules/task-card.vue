<!-- 看板任务卡片（紧凑版 2026-09-10，高度约为原五行结构的一半）：
     ① 标题行（任务名+说明图标+子任务计数徽标）② 元信息行（优先级+执行人+计划完成+工时内联编辑）③ 子任务标识与补充信息行（均无则不渲染）
     2026-09-11 用户需求：边框由 Tailwind 默认 currentColor（显黑）改为主题色描边（border-theme/30）+ 轻阴影（shadow-xs），与列容器区分、凸显卡片 -->
<template>
  <div
    class="mb-1.5 cursor-grab rounded-md border border-solid border-theme/30 p-2 shadow-xs"
    :class="[task.status === '已完成' ? 'opacity-[0.55]' : '', depth ? 'ml-3.5' : '']"
    :data-task-id="task.id"
    @dblclick="handleCardDblClick"
  >
    <!-- ① 标题行 -->
    <div class="flex items-center gap-1">
      <ElTooltip :content="task.desc || task.title" placement="top" :show-after="300">
        <div class="flex min-w-0 flex-1 items-center gap-1">
          <span class="min-w-0 flex-1 truncate text-[13px] font-medium leading-5">{{ task.title }}</span>
          <ArtSvgIcon v-if="task.desc" icon="ri:file-text-line" class="shrink-0 text-xs text-theme" />
        </div>
      </ElTooltip>
      <ElTooltip
        v-if="task.childCount > 0"
        :content="t('system.task.childCountTip', { count: task.childCount })"
        :show-after="300"
      >
        <span class="shrink-0 rounded-full bg-g-200 px-1 text-xs leading-4 text-g-600">+{{ task.childCount }}</span>
      </ElTooltip>
    </div>

    <!-- ② 元信息行：优先级 + 执行人 + 计划完成 + 工时（双击内联编辑） -->
    <div class="mt-1 flex items-center gap-1.5 text-xs">
      <ElTag size="small" :type="priorityTagType">{{ task.priority }}</ElTag>
      <span class="min-w-0 flex-1 truncate">{{ task.assignee || '—' }}</span>
      <span v-if="planLabel" class="shrink-0 truncate text-theme">{{ planLabel }}</span>
      <ElInputNumber
        v-if="editing"
        ref="hoursInputRef"
        v-model="draft"
        size="small"
        :min="0"
        :controls="false"
        class="w-16 shrink-0"
        @blur="commitHours"
        @keydown.enter="commitHours"
        @keydown.esc="cancelHours"
      />
      <span
        v-else
        class="shrink-0 cursor-pointer"
        :class="{ 'cursor-not-allowed': readonly }"
        @dblclick.stop="startEditHours"
      >
        {{ task.hours !== null && task.hours !== undefined ? `${task.hours}h` : '—h' }}
      </span>
    </div>

    <!-- ③ 子任务标识（品牌色小字）+ 补充信息（自动隐藏已分组维度，父组件拼好） -->
    <div v-if="task.parentId || extra" class="mt-1 flex items-center gap-1.5 text-xs">
      <span v-if="task.parentId" class="min-w-0 shrink truncate text-theme">
        {{ parentTitle ? t('system.task.orphanChild', { parent: parentTitle }) : `↳ ${t('system.task.childBadge')}` }}
      </span>
      <span v-if="extra" class="min-w-0 flex-1 truncate text-g-500">{{ extra }}</span>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'TaskCard' })

  const props = defineProps<{
    task: Api.Task.TaskListItem
    /** 补充信息（行③，父组件按分组维度拼好；空串不渲染） */
    extra?: string
    /** 孤儿子任务的父任务标题 */
    parentTitle?: string
    /** 缩进层数（每级 14px；任务树仅两级，取 0/1） */
    depth?: number
    /** 只读：禁工时编辑与双击打开编辑 */
    readonly?: boolean
  }>()

  const emit = defineEmits<{
    'open-edit': [task: Api.Task.TaskListItem]
    'hours-commit': [payload: { id: string; value: number }]
  }>()

  const { t } = useI18n()

  const priorityTagType = computed(() => {
    switch (props.task.priority) {
      case '高':
        return 'danger'
      case '中':
        return 'warning'
      default:
        return 'info'
    }
  })

  /** 计划完成：planDate 日期优先，否则第 N 周；均无则不显示 */
  const planLabel = computed(() => {
    if (props.task.planDate) return props.task.planDate
    if (props.task.week !== null && props.task.week !== undefined) {
      return t('system.task.weekLabel', { n: props.task.week })
    }
    return ''
  })

  const handleCardDblClick = () => {
    if (!props.readonly) emit('open-edit', props.task)
  }

  // ==================== 工时内联编辑（失焦/回车提交，Esc 取消） ====================

  const editing = ref(false)
  const draft = ref(0)
  const hoursInputRef = useTemplateRef<{ focus: () => void }>('hoursInputRef')

  const startEditHours = () => {
    if (props.readonly) return
    editing.value = true
    draft.value = props.task.hours ?? 0
    nextTick(() => hoursInputRef.value?.focus())
  }

  const commitHours = () => {
    if (!editing.value) return
    editing.value = false
    const value = draft.value ?? 0
    if (value !== (props.task.hours ?? 0)) {
      emit('hours-commit', { id: props.task.id, value })
    }
  }

  const cancelHours = () => {
    editing.value = false
  }
</script>
