<!-- 任务看板（08a）：5 维度自由多选分组（多选生成交叉列，第一个维度=主维度决定拖拽写回字段）+ 内容筛选 + 主维度拖拽写回；
     restricted 受限形态（详情页进度看板预留）：固定状态四列、无内容筛选、按计划完成月份过滤、仅状态列间拖拽 -->
<!-- 注：子系统维度 2026-09-10 起从看板下线（分组 Tag 与内容筛选均移除；卡片补充信息仍展示子系统名称） -->
<template>
  <div>
    <!-- 受限形态：年月过滤（按计划完成时间所属月份） -->
    <div v-if="restricted" class="mt-2 flex items-center gap-3">
      <ElDatePicker
        v-model="month"
        type="month"
        value-format="YYYY-MM"
        format="YYYY-MM"
        :clearable="false"
        class="w-40"
      />
    </div>

    <!-- ② 分组维度条：已选高亮（蓝），点击追加/移除，至少保留一个 -->
    <div v-if="!restricted" class="mt-2 flex flex-wrap items-center gap-2">
      <span class="text-sm">{{ t('system.task.groupDimensions') }}：</span>
      <ElTag
        v-for="dim in visibleDims"
        :key="dim.key"
        class="cursor-pointer select-none"
        :type="groupDims.includes(dim.key) ? 'primary' : 'info'"
        @click="toggleDim(dim.key)"
      >
        {{ dim.label }}
      </ElTag>
      <span class="text-xs text-g-500">
        {{ readonly ? t('system.task.readOnlyHint') : t('system.task.dragTip') }}
      </span>
    </div>

    <!-- ③ 内容筛选条：每维度一个下拉（AND 组合），只过滤卡片不改列结构；单行横向排列，宽度自适应不折行 -->
    <div v-if="!restricted" class="mt-2 flex flex-nowrap items-center gap-2">
      <span class="shrink-0 text-sm">{{ t('system.task.contentFilter') }}：</span>
      <ElSelect
        v-for="dim in visibleDims"
        :key="dim.key"
        v-model="dimFilters[dim.key]"
        clearable
        class="min-w-0 flex-1"
        :placeholder="`${dim.label}${t('system.task.filterAll')}`"
      >
        <ElOption v-for="value in dimOptions(dim.key)" :key="value" :label="value" :value="value" />
      </ElSelect>
      <ElButton v-if="hasFilter" v-ripple class="shrink-0" @click="clearFilters">
        {{ t('system.task.clearFilters') }}
      </ElButton>
      <span class="min-w-0 truncate text-xs text-g-500">
        {{ hasFilter ? t('system.task.filteredRemaining', { count: filteredFlat.length }) : t('system.task.filterHint') }}
      </span>
    </div>

    <!-- ④ 看板列区（列数超视口横向滚动） -->
    <div class="mt-3 flex items-start gap-3 overflow-x-auto pb-2">
      <KanbanColumn
        v-for="col in columns"
        :key="col.key"
        v-model:items="col.items"
        :title="col.title"
        :accent="accentOf(col)"
        :extras="extras"
        :parent-title-map="parentTitleMap"
        :drag-enabled="dragEnabled"
        :readonly="readonly"
        :allow-empty="allowEmpty"
        :empty-tip="t('system.task.emptyColumnTip')"
        @open-edit="(task) => emit('open-form', task)"
        @hours-commit="(payload) => emit('hours-commit', payload)"
        @drop-end="onDropEnd"
      />
    </div>
  </div>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import KanbanColumn from './kanban-column.vue'

  defineOptions({ name: 'KanbanView' })

  type GroupKey = 'status' | 'priority' | 'assignee' | 'group' | 'deadline'

  const props = withDefaults(
    defineProps<{
      tasks: Api.Task.TaskListItem[]
      loading: boolean
      /** 只读：禁拖拽、禁工时编辑、禁双击打开编辑 */
      readonly?: boolean
      /** 受限形态（详情页进度看板预留）：状态固定四列、无内容筛选、按月份过滤、仅状态拖拽 */
      restricted?: boolean
    }>(),
    { readonly: false, restricted: false }
  )

  const emit = defineEmits<{
    'open-form': [task?: Api.Task.TaskListItem]
    /** 主维度写回：拖拽落点列第一段为写回值（'—' 清空） */
    'field-change': [payload: { id: string; field: string; value: string; display: string }]
    'hours-commit': [payload: { id: string; value: number }]
  }>()

  const { t } = useI18n()

  /** 维度字段映射（拖拽写回用） */
  const dimFieldMap: Record<GroupKey, string> = {
    status: 'status',
    priority: 'priority',
    assignee: 'assignee',
    group: 'group',
    deadline: 'deadline'
  }

  /** 固定顺序维度（列排序 / 筛选候选值排序基准） */
  const fixedOrder: Partial<Record<GroupKey, string[]>> = {
    status: ['待处理', '进行中', '已完成', '受阻'],
    priority: ['高', '中', '低']
  }

  /** 分组维度（2026-09-10 起子系统维度已下线，看板为 5 维度） */
  const dimKeys: GroupKey[] = ['status', 'priority', 'assignee', 'group', 'deadline']

  const dimLabel = (key: GroupKey): string => {
    switch (key) {
      case 'status':
        return t('system.task.dimensionStatus')
      case 'priority':
        return t('system.task.dimensionPriority')
      case 'assignee':
        return t('system.task.dimensionAssignee')
      case 'group':
        return t('system.task.dimensionGroup')
      default:
        return t('system.task.dimensionDeadline')
    }
  }

  /** 全量扁平（父 + 子） */
  const flatTasks = computed<Api.Task.TaskListItem[]>(() =>
    props.tasks.flatMap((task) => [task, ...(task.children || [])])
  )

  const visibleDims = computed(() => dimKeys.map((key) => ({ key, label: dimLabel(key) })))

  // ==================== 分组维度 ====================

  /** 已选分组维度序列（有序，默认任务状态；受限形态恒为状态） */
  const groupDims = ref<GroupKey[]>(['status'])
  const activeDims = computed<GroupKey[]>(() => (props.restricted ? ['status'] : groupDims.value))

  const toggleDim = (dim: GroupKey) => {
    const idx = groupDims.value.indexOf(dim)
    if (idx >= 0) {
      if (groupDims.value.length === 1) {
        ElMessage.warning(t('system.task.noGroupDimensions'))
        return
      }
      // 整体重赋值触发 activeDims / 列重建的响应式更新
      groupDims.value = groupDims.value.filter((key) => key !== dim)
    } else {
      groupDims.value = [...groupDims.value, dim]
    }
  }

  // ==================== 内容筛选 ====================

  const dimFilters = reactive<Record<GroupKey, string>>({
    status: '',
    priority: '',
    assignee: '',
    group: '',
    deadline: ''
  })

  const hasFilter = computed(() => dimKeys.some((key) => !!dimFilters[key]))

  const clearFilters = () => {
    dimKeys.forEach((key) => (dimFilters[key] = ''))
  }

  /** 维度候选值：当前任务集实际出现的值去重；状态/优先级按固定顺序，其余按出现顺序 */
  const dimOptions = (dim: GroupKey): string[] => {
    const values = [...new Set(monthTasks.value.map((task) => dimValue(task, dim)))].filter((v) => v !== '—')
    const order = fixedOrder[dim]
    if (order) return order.filter((v) => values.includes(v))
    return values
  }

  // ==================== 任务集（月份过滤 → 内容筛选 AND） ====================

  /** 受限形态：按计划完成时间所属月份过滤（默认当前月） */
  const month = ref(currentMonth())

  const monthTasks = computed(() => {
    if (!props.restricted) return flatTasks.value
    return flatTasks.value.filter((task) => (task.planDate || '').slice(0, 7) === month.value)
  })

  /** 内容筛选：多维度 AND（只过滤卡片，不改变列结构） */
  const filteredFlat = computed(() =>
    monthTasks.value.filter((task) =>
      dimKeys.every((dim) => {
        const filter = dimFilters[dim]
        return !filter || dimValue(task, dim) === filter
      })
    )
  )

  // ==================== 列生成 ====================

  interface ColumnModel {
    key: string
    title: string
    /** 列名第一段（主维度取值，拖拽写回基准） */
    mainValue: string
    items: Api.Task.TaskListItem[]
  }

  const columns = ref<ColumnModel[]>([])

  /** 任务在该维度上的取值（空/缺失统一 '—'） */
  const dimValue = (task: Api.Task.TaskListItem, dim: GroupKey): string => {
    switch (dim) {
      case 'status':
        return task.status
      case 'priority':
        return task.priority
      case 'assignee':
        return task.assignee || '—'
      case 'group':
        return task.group || '—'
      default:
        return task.deadline || '—'
    }
  }

  /** 维度值排序基准：固定顺序优先，其余按任务集首次出现顺序 */
  const valueRank = (dim: GroupKey): Map<string, number> => {
    const rank = new Map<string, number>()
    const order = fixedOrder[dim]
    if (order) order.forEach((v, i) => rank.set(v, i))
    filteredFlat.value.forEach((task) => {
      const v = dimValue(task, dim)
      if (!rank.has(v)) rank.set(v, rank.size)
    })
    return rank
  }

  /** 列内树形排序：父在前子紧跟其后，孤儿子任务（父不在本列）追加列尾 */
  const treeOrder = (items: Api.Task.TaskListItem[]): Api.Task.TaskListItem[] => {
    const ids = new Set(items.map((task) => task.id))
    const out: Api.Task.TaskListItem[] = []
    const appended = new Set<string>()
    for (const task of items) {
      if (task.parentId && ids.has(task.parentId)) continue
      out.push(task)
      appended.add(task.id)
      for (const child of items) {
        if (child.parentId === task.id && !appended.has(child.id)) {
          out.push(child)
          appended.add(child.id)
        }
      }
    }
    for (const task of items) {
      if (!appended.has(task.id)) out.push(task)
    }
    return out
  }

  /** 列生成：状态单选四列固定（含空列）；其余只生成有任务的（交叉）列 */
  const buildColumns = (): ColumnModel[] => {
    const dims = activeDims.value
    if (dims.length === 1 && dims[0] === 'status') {
      const cols = (fixedOrder.status || []).map((v) => ({
        key: v,
        title: v,
        mainValue: v,
        items: [] as Api.Task.TaskListItem[]
      }))
      for (const task of filteredFlat.value) {
        const col = cols.find((c) => c.key === task.status)
        if (col) col.items.push(task)
      }
      cols.forEach((col) => (col.items = treeOrder(col.items)))
      return cols
    }
    const ranks = dims.map((dim) => valueRank(dim))
    const groups = new Map<string, Api.Task.TaskListItem[]>()
    for (const task of filteredFlat.value) {
      const key = dims.map((dim) => dimValue(task, dim)).join('')
      if (!groups.has(key)) groups.set(key, [])
      groups.get(key)!.push(task)
    }
    const cols: ColumnModel[] = [...groups.entries()].map(([key, items]) => {
      const segs = key.split('')
      return { key, title: segs.join(' · '), mainValue: segs[0], items: treeOrder(items) }
    })
    cols.sort((a, b) => {
      const av = a.key.split('')
      const bv = b.key.split('')
      for (let i = 0; i < av.length; i++) {
        const diff =
          (ranks[i]?.get(av[i]) ?? Number.MAX_SAFE_INTEGER) - (ranks[i]?.get(bv[i]) ?? Number.MAX_SAFE_INTEGER)
        if (diff !== 0) return diff
      }
      return 0
    })
    return cols
  }

  watch([filteredFlat, activeDims], () => {
    columns.value = buildColumns()
  }, { immediate: true })

  /** 仅状态单选保留空列 */
  const allowEmpty = computed(() => activeDims.value.length === 1 && activeDims.value[0] === 'status')

  /** 列头色块：主维度取列名第一段；语义色走全局变量 */
  const accentOf = (col: ColumnModel): string => {
    const main = activeDims.value[0]
    if (main === 'status') {
      const map: Record<string, string> = {
        待处理: 'var(--art-gray-500)',
        进行中: 'var(--art-primary)',
        已完成: 'var(--art-success)',
        受阻: 'var(--art-danger)'
      }
      return map[col.mainValue] || 'var(--art-primary)'
    }
    if (main === 'priority') {
      const map: Record<string, string> = {
        高: 'var(--art-danger)',
        中: 'var(--art-warning)',
        低: 'var(--art-gray-500)'
      }
      return map[col.mainValue] || 'var(--art-primary)'
    }
    return 'var(--art-primary)'
  }

  // ==================== 卡片辅助数据 ====================

  /** 父任务标题映射（孤儿子任务标注用） */
  const parentTitleMap = computed<Record<string, string>>(() => {
    const map: Record<string, string> = {}
    for (const task of flatTasks.value) {
      map[task.id] = task.title
    }
    return map
  })

  /** 补充信息（行③）：自动隐藏已被分组维度覆盖的项；子系统已从分组维度下线，恒展示 */
  const extras = computed<Record<string, string>>(() => {
    const dims = activeDims.value
    const map: Record<string, string> = {}
    for (const task of flatTasks.value) {
      const segs: string[] = []
      if (!dims.includes('deadline') && task.deadline) segs.push(task.deadline)
      if (!dims.includes('assignee') && task.assignee) segs.push(task.assignee)
      if (!dims.includes('group') && task.group) segs.push(task.group)
      if (task.subsystemName) segs.push(task.subsystemName)
      map[task.id] = segs.join(' · ')
    }
    return map
  })

  // ==================== 拖拽写回 ====================

  /** 只读禁拖（子系统维度已下线，无只读主维度限制） */
  const dragEnabled = computed(() => !props.readonly)

  /** 落点解析：目标列名（交叉列含 ' · '）取第一段作为写回值 */
  const onDropEnd = (taskId: string) => {
    const task = flatTasks.value.find((item) => item.id === taskId)
    if (!task) {
      columns.value = buildColumns()
      return
    }
    const target = columns.value.find((col) => col.items.some((item) => item.id === taskId))
    // 值未变化（含同列位置调整 / 第一段与现值一致）：不发请求，恢复树形排序
    if (!target || dimValue(task, activeDims.value[0]) === target.mainValue) {
      columns.value = buildColumns()
      return
    }
    emit('field-change', {
      id: task.id,
      field: dimFieldMap[activeDims.value[0]],
      value: target.mainValue === '—' ? '' : target.mainValue,
      display: target.mainValue
    })
  }

  function currentMonth(): string {
    const now = new Date()
    return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`
  }

  defineExpose({ month })
</script>
