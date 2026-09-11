<!-- 字典枚举项管理抽屉（动态扩展字段渲染：enum 下拉 / color 色块） -->
<template>
  <ElDrawer
    v-model="visible"
    :title="`${t('system.dict.itemManage')} - ${dictName}`"
    size="760px"
    append-to-body
  >
    <div class="px-4">
      <div class="mb-4 flex items-center justify-between">
        <ElButton type="primary" v-ripple @click="openItemForm()">
          <ArtSvgIcon icon="ri:add-line" class="mr-1" />
          {{ t('common.add') }}
        </ElButton>
        <ElInput
          v-model="kw"
          :placeholder="t('table.searchBar.searchInputPlaceholder')"
          clearable
          class="max-w-60"
          @keyup.enter="loadItems"
          @clear="loadItems"
        >
          <template #prefix>
            <ArtSvgIcon icon="ri:search-line" />
          </template>
        </ElInput>
      </div>

      <ArtTable ref="tableRef" :loading="loading" :data="items" :columns="columns" row-key="id">
        <!-- 2026-09-11 用户需求：↑↓ 按钮改为拖动排序（整行可拖），序号按位置实时重排 -->
        <template #sort="{ $index }">
          <div class="flex items-center gap-2">
            <ArtSvgIcon icon="ri:drag-move-2-fill" class="cursor-move text-g-500" />
            <span class="text-sm">{{ $index + 1 }}</span>
          </div>
        </template>

        <template #status="{ row }">
          <ElSwitch
            :model-value="row.status === 1"
            :loading="statusLoadingId === row.id"
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>

        <template #operation="{ row }">
          <div class="no-drag flex items-center">
            <ArtButtonTable type="edit" @click="openItemForm(row)" />
            <ArtButtonTable type="delete" @click="handleDelete(row)" />
          </div>
        </template>
      </ArtTable>
    </div>
  </ElDrawer>

  <!-- 枚举项新增 / 编辑弹窗（扩展字段按字段定义动态生成） -->
  <ElDialog
    v-model="itemFormVisible"
    :title="itemFormTitle"
    width="640px"
    :close-on-click-modal="false"
    append-to-body
  >
    <ArtForm
      ref="itemFormRef"
      v-model="itemFormData"
      :items="itemFormItems"
      :rules="formRules"
      :show-reset="false"
      :show-submit="false"
    />
    <template #footer>
      <ElButton @click="itemFormVisible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton type="primary" v-ripple @click="handleItemSubmit">
        {{ t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import {
    fetchChangeDictItemStatus,
    fetchCreateDictItem,
    fetchDeleteDictItem,
    fetchDictFields,
    fetchDictItems,
    fetchReorderDictItems,
    fetchUpdateDictItem
  } from '@/api/dict'
  import { ElColorPicker, ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useDraggable } from 'vue-draggable-plus'
  import type { ColumnOption } from '@/types'

  defineOptions({ name: 'ItemDrawer' })

  const { t } = useI18n()

  const visible = defineModel<boolean>('visible', { default: false })
  const itemFormRef = useTemplateRef<{
    validate: () => Promise<boolean>
    reset: () => void
    getOutput: () => Record<string, any>
  }>('itemFormRef')

  const dict = ref<Api.Dict.DictListItem>()
  const dictName = ref('')
  const items = ref<Api.Dict.DictItem[]>([])
  const fields = ref<Api.Dict.DictField[]>([])
  const kw = ref('')
  const loading = ref(false)

  /** 自定义字段（非系统默认，用于扩展值渲染与动态表单） */
  const customFields = computed(() =>
    fields.value.filter((field) => field.isSystem !== 1 && field.status === 1)
  )

  const columns = computed<ColumnOption[]>(() => [
    { prop: 'sort', label: t('system.dict.sort'), width: 90, useSlot: true },
    { prop: 'code', label: t('system.dict.itemCode'), minWidth: 120 },
    { prop: 'name', label: t('system.dict.itemName'), minWidth: 110 },
    // 2026-09-11 用户需求：去掉该列（原 label 用的 system.dict.field 文案键不存在，表头显示成了 key 本身）
    { prop: 'status', label: t('common.status'), width: 80, useSlot: true },
    { prop: 'operation', label: t('common.operation'), width: 100, fixed: 'right', useSlot: true }
  ])

  const statusLoadingId = ref<number | null>(null)

  const loadItems = async () => {
    if (!dict.value) return
    loading.value = true
    try {
      const [itemList, fieldList] = await Promise.all([
        fetchDictItems(dict.value.id, kw.value || undefined),
        fetchDictFields(dict.value.id)
      ])
      items.value = itemList
      fields.value = fieldList
    } finally {
      loading.value = false
    }
    // 行由本次渲染重建，重新绑定拖拽
    bindDrag()
  }

  const open = (row: Api.Dict.DictListItem) => {
    dict.value = row
    dictName.value = row.name
    kw.value = ''
    visible.value = true
    loadItems()
  }

  const handleStatusChange = async (row: Api.Dict.DictItem, val: string | number | boolean) => {
    statusLoadingId.value = row.id
    try {
      await fetchChangeDictItemStatus({ id: row.id, status: val ? 1 : 0 })
      ElMessage.success(t('common.operateSuccess'))
      loadItems()
    } finally {
      statusLoadingId.value = null
    }
  }

  // ==================== 拖动排序（2026-09-11 用户需求） ====================

  /** ArtTable 暴露的实例（只用 elTableRef 定位表格 DOM 以挂载拖拽） */
  interface ArtTableInstance {
    elTableRef?: { $el?: HTMLElement }
  }
  const tableRef = useTemplateRef<ArtTableInstance>('tableRef')
  const tbodyEl = ref<HTMLElement | null>(null)

  /** 拖动结束：本地数组已按新顺序，序号按位置 1..n 重写并回写服务端；失败回读服务端顺序 */
  const persistOrder = async () => {
    if (!dict.value) return
    items.value.forEach((item, index) => (item.sort = index + 1))
    try {
      await fetchReorderDictItems({ dictId: dict.value.id, ids: items.value.map((i) => i.id) })
    } catch {
      loadItems()
    }
  }

  const { start: startDrag } = useDraggable<Api.Dict.DictItem>(tbodyEl, items, {
    immediate: false,
    animation: 150,
    draggable: 'tr',
    // 行内按钮不参与拖动
    filter: '.no-drag',
    preventOnFilter: false,
    onEnd: () => persistOrder()
  })

  /** 绑定拖拽：需抽屉与表格渲染完成；关键词过滤时列表非全量，不启用拖动 */
  const bindDrag = async () => {
    await nextTick()
    const root = tableRef.value?.elTableRef?.$el
    const tbody = (root?.querySelector('.el-table__body tbody') ||
      root?.querySelector('tbody')) as HTMLElement | null
    if (!tbody || kw.value || !items.value.length) {
      return
    }
    tbodyEl.value = tbody
    startDrag()
  }

  const handleDelete = async (row: Api.Dict.DictItem) => {
    await ElMessageBox.confirm(t('system.dict.deleteItemTips'), t('common.tips'), {
      type: 'warning'
    })
    await fetchDeleteDictItem({ id: row.id })
    ElMessage.success(t('common.deleteSuccess'))
    loadItems()
  }

  // ==================== 枚举项新增 / 编辑 ====================

  const itemFormVisible = ref(false)
  const isItemEdit = ref(false)
  const editingItemId = ref<number>()
  const itemFormData = ref<Record<string, any>>({})

  const itemFormTitle = computed(() => (isItemEdit.value ? t('common.edit') : t('common.add')))

  // 2026-09-11 用户需求：去掉「排序」字段（改由拖动排序），其余字段竖向单列
  const itemFormItems = computed(() => {
    const formItems: any[] = [
      {
        key: 'name',
        label: t('system.dict.itemName'),
        type: 'input',
        span: 24,
        props: { placeholder: t('system.dict.itemNamePlaceholder'), maxlength: 128 }
      },
      {
        key: 'code',
        label: t('system.dict.itemCode'),
        type: 'input',
        span: 24,
        props: {
          placeholder: t('system.dict.itemCodePlaceholder'),
          maxlength: 64,
          disabled: isItemEdit.value
        }
      }
    ]
    for (const field of customFields.value) {
      const key = `ext_${field.fieldCode}`
      if (field.fieldCode === 'color') {
        formItems.push({ key, label: field.fieldName, type: 'input', render: ElColorPicker, span: 24 })
      } else if (field.fieldType === 'enum') {
        formItems.push({
          key,
          label: field.fieldName,
          type: 'select',
          span: 24,
          props: {
            placeholder: t('system.dict.optionsPlaceholder'),
            options: (field.options || []).map((option: string) => ({ label: option, value: option }))
          }
        })
      } else {
        formItems.push({
          key,
          label: field.fieldName,
          type: 'input',
          span: 24,
          props: { placeholder: t('system.dict.extValuePlaceholder'), maxlength: 255 }
        })
      }
    }
    return formItems
  })

    /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      name: [{ required: true, message: t('system.dict.itemNamePlaceholder') }],
      code: [{ required: true, message: t('system.dict.itemCodePlaceholder') }],
    }
    return rules
  })
  const openItemForm = (row?: Api.Dict.DictItem) => {
    isItemEdit.value = !!row
    editingItemId.value = row?.id
    const data: Record<string, any> = row ? { name: row.name, code: row.code } : {}
    for (const field of customFields.value) {
      const key = `ext_${field.fieldCode}`
      data[key] = row?.ext?.[field.fieldCode] ?? (field.defaultValue || undefined)
    }
    itemFormData.value = data
    itemFormVisible.value = true
  }

  const handleItemSubmit = async () => {
    if (!dict.value) return
    await itemFormRef.value?.validate()
    const { name, code } = itemFormData.value
    if (!name?.trim()) {
      ElMessage.warning(t('system.dict.itemNamePlaceholder'))
      return
    }
    if (!code?.trim()) {
      ElMessage.warning(t('system.dict.itemCodePlaceholder'))
      return
    }
    // 按字段定义收集扩展值（必填校验）
    const ext: Record<string, any> = {}
    for (const field of customFields.value) {
      const value = itemFormData.value[`ext_${field.fieldCode}`]
      const isEmpty = value === null || value === undefined || String(value).trim() === ''
      if (field.isRequired === 1 && isEmpty) {
        ElMessage.warning(`${field.fieldName}${t('system.dict.isRequired')}`)
        return
      }
      if (!isEmpty) {
        ext[field.fieldCode] = value
      }
    }
    // 不传 sort：新增由后端追加到末尾（按时间排序），编辑保持既有顺序
    const payload: Api.Dict.DictItemSaveParams = {
      id: editingItemId.value,
      dictId: dict.value.id,
      code: code.trim(),
      name: name.trim(),
      ext: Object.keys(ext).length ? ext : null
    }
    if (isItemEdit.value) {
      await fetchUpdateDictItem(payload)
    } else {
      await fetchCreateDictItem(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    itemFormVisible.value = false
    loadItems()
  }

  defineExpose({ open })
</script>
