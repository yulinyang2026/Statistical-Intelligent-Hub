<!-- 看板列（08a）：列头（色块+列名+数量徽标）+ SortableJS 卡片列表（组内跨列拖拽，动画 150ms）+ 空态 -->
<template>
  <div class="w-72 shrink-0">
    <!-- 列头 -->
    <div class="art-card-sm flex items-center gap-2 px-3 py-2">
      <span class="h-2.5 w-2.5 shrink-0 rounded-sm" :style="{ backgroundColor: accent }" />
      <span class="truncate text-[13px] font-semibold">{{ title }}</span>
      <span class="ml-auto shrink-0 rounded-full bg-g-200 px-1.5 text-xs leading-5 text-g-600">{{ items.length }}</span>
    </div>

    <!-- 卡片列表（vue-draggable-plus 多实例同 group 即跨列移动） -->
    <VueDraggable
      v-model="list"
      :animation="150"
      group="task-kanban"
      item-key="id"
      :disabled="!dragEnabled"
      class="art-card mt-2 min-h-[96px] p-2.5"
      @end="onDragEnd"
    >
      <TaskCard
        v-for="task in list"
        :key="task.id"
        :task="task"
        :extra="extras[task.id]"
        :parent-title="task.parentId ? parentTitleMap[task.parentId] : undefined"
        :depth="task.parentId ? 1 : 0"
        :readonly="readonly"
        @open-edit="(task) => emit('open-edit', task)"
        @hours-commit="(payload) => emit('hours-commit', payload)"
      />
      <div v-if="!list.length && allowEmpty" class="py-3 text-center text-xs text-g-500">
        {{ emptyTip }}
      </div>
    </VueDraggable>
  </div>
</template>

<script setup lang="ts">
  import { VueDraggable } from 'vue-draggable-plus'
  import TaskCard from './task-card.vue'

  defineOptions({ name: 'KanbanColumn' })

  const props = defineProps<{
    title: string
    /** 列头色块颜色（CSS 变量引用，如 var(--art-primary)） */
    accent: string
    items: Api.Task.TaskListItem[]
    extras: Record<string, string>
    parentTitleMap: Record<string, string>
    dragEnabled: boolean
    readonly: boolean
    /** 仅状态单选保留空列（列内显示「暂无任务」） */
    allowEmpty: boolean
    emptyTip: string
  }>()

  const emit = defineEmits<{
    'update:items': [items: Api.Task.TaskListItem[]]
    'open-edit': [task: Api.Task.TaskListItem]
    'hours-commit': [payload: { id: string; value: number }]
    'drop-end': [taskId: string]
  }>()

  /** 双向列表代理：跨列移动时由 vue-draggable-plus 写回父级列模型 */
  const list = computed<Api.Task.TaskListItem[]>({
    get: () => props.items,
    set: (value) => emit('update:items', value)
  })

  const onDragEnd = (evt: { item?: HTMLElement }) => {
    const taskId = evt.item?.dataset?.taskId
    if (taskId) emit('drop-end', taskId)
  }
</script>
