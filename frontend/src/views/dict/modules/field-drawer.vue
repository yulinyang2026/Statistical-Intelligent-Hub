<!-- 字典字段管理抽屉（字段编码不可改；停用而非删除） -->
<template>
  <ElDrawer
    v-model="visible"
    :title="`${t('system.dict.fieldManage')} - ${dictName}`"
    size="560px"
    append-to-body
  >
    <div class="px-4">
      <div class="mb-4 flex items-center justify-between">
        <ElButton type="primary" v-ripple @click="openFieldForm()">
          <ArtSvgIcon icon="ri:add-line" class="mr-1" />
          {{ t('common.add') }}
        </ElButton>
      </div>
      <ElAlert
        type="info"
        :closable="false"
        show-icon
        :title="t('system.dict.fieldStatusTips')"
        class="mb-4"
      />
      <ArtTable :loading="loading" :data="fields" :columns="columns" row-key="id">
        <template #fieldType="{ row }">
          <ElTag :type="row.fieldType === 'enum' ? 'warning' : 'info'">
            {{ row.fieldType === 'enum' ? t('system.dict.typeEnum') : t('system.dict.typeString') }}
          </ElTag>
        </template>
        <template #flags="{ row }">
          <span class="text-sm">
            {{ row.isRequired === 1 ? `${t('system.dict.isRequired')} · ` : '' }}
            {{ row.isUnique === 1 ? t('system.dict.isUnique') : '' }}
            {{ row.isSystem === 1 ? ` · ${t('system.dict.builtin')}` : '' }}
          </span>
        </template>
        <template #status="{ row }">
          <ElSwitch
            :model-value="row.status === 1"
            :disabled="row.isSystem === 1"
            :loading="statusLoadingId === row.id"
            @change="(val) => handleStatusChange(row, val)"
          />
        </template>
        <template #operation="{ row }">
          <div class="flex items-center">
            <ArtButtonTable v-if="row.isSystem !== 1" type="edit" @click="openFieldForm(row)" />
            <span v-else class="text-sm">{{ t('system.dict.systemFieldTips') }}</span>
          </div>
        </template>
      </ArtTable>
    </div>
  </ElDrawer>

  <!-- 字段新增 / 编辑弹窗 -->
  <ElDialog
    v-model="fieldFormVisible"
    :title="fieldFormTitle"
    width="560px"
    :close-on-click-modal="false"
    append-to-body
  >
    <ArtForm
      ref="fieldFormRef"
      v-model="fieldFormData"
      :items="fieldFormItems"
      :rules="formRules"
      :show-reset="false"
      :show-submit="false"
    />
    <template #footer>
      <ElButton @click="fieldFormVisible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton type="primary" v-ripple @click="handleFieldSubmit">
        {{ t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import {
    fetchChangeDictFieldStatus,
    fetchCreateDictField,
    fetchDictFields,
    fetchUpdateDictField
  } from '@/api/dict'
  import { useI18n } from 'vue-i18n'
  import type { ColumnOption } from '@/types'

  defineOptions({ name: 'FieldDrawer' })

  const { t } = useI18n()

  const visible = defineModel<boolean>('visible', { default: false })
  const formRef = useTemplateRef<{
    validate: () => Promise<boolean>
    reset: () => void
    getOutput: () => Record<string, any>
  }>('fieldFormRef')

  const dict = ref<Api.Dict.DictListItem>()
  const dictName = ref('')
  const fields = ref<Api.Dict.DictField[]>([])
  const loading = ref(false)

  const columns = computed<ColumnOption[]>(() => [
    { type: 'index', label: t('common.index'), width: 60 },
    { prop: 'fieldCode', label: t('system.dict.fieldCode'), minWidth: 110 },
    { prop: 'fieldName', label: t('system.dict.fieldName'), minWidth: 110 },
    { prop: 'fieldType', label: t('system.dict.fieldType'), width: 90, useSlot: true },
    { prop: 'flags', label: t('system.dict.isRequired'), width: 150, useSlot: true },
    { prop: 'options', label: t('system.dict.options'), minWidth: 140 },
    { prop: 'defaultValue', label: t('system.dict.defaultValue'), minWidth: 100 },
    { prop: 'sort', label: t('system.dict.sort'), width: 70 },
    { prop: 'status', label: t('common.status'), width: 80, useSlot: true },
    { prop: 'operation', label: t('common.operation'), width: 110, fixed: 'right', useSlot: true }
  ])

  const statusLoadingId = ref<number | null>(null)

  const loadFields = async () => {
    if (!dict.value) return
    loading.value = true
    try {
      fields.value = await fetchDictFields(dict.value.id)
    } finally {
      loading.value = false
    }
  }

  const open = (row: Api.Dict.DictListItem) => {
    dict.value = row
    dictName.value = row.name
    visible.value = true
    loadFields()
  }

  const handleStatusChange = async (row: Api.Dict.DictField, val: string | number | boolean) => {
    statusLoadingId.value = row.id
    try {
      await fetchChangeDictFieldStatus({ id: row.id, status: val ? 1 : 0 })
      ElMessage.success(t('common.operateSuccess'))
      loadFields()
    } finally {
      statusLoadingId.value = null
    }
  }

  // ==================== 字段新增 / 编辑 ====================

  const fieldFormVisible = ref(false)
  const isFieldEdit = ref(false)
  const editingFieldId = ref<number>()
  const fieldFormData = ref<Record<string, any>>({})

  const fieldFormTitle = computed(() => (isFieldEdit.value ? t('common.edit') : t('common.add')))

  const fieldFormItems = computed(() => [
    {
      key: 'fieldName',
      label: t('system.dict.fieldName'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.dict.fieldNamePlaceholder'), maxlength: 64 }
    },
    {
      key: 'fieldCode',
      label: t('system.dict.fieldCode'),
      type: 'input',
      span: 12,
      props: {
        placeholder: t('system.dict.fieldCodePlaceholder'),
        maxlength: 64,
        disabled: isFieldEdit.value
      }
    },
    {
      key: 'fieldType',
      label: t('system.dict.fieldType'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.dict.fieldTypePlaceholder'),
        options: [
          { label: t('system.dict.typeString'), value: 'string' },
          { label: t('system.dict.typeEnum'), value: 'enum' }
        ]
      }
    },
    {
      key: 'isRequired',
      label: t('system.dict.isRequired'),
      type: 'select',
      span: 12,
      props: {
        options: [
          { label: t('common.yes'), value: 1 },
          { label: t('common.no'), value: 0 }
        ]
      }
    },
    {
      key: 'isUnique',
      label: t('system.dict.isUnique'),
      type: 'select',
      span: 12,
      props: {
        options: [
          { label: t('common.yes'), value: 1 },
          { label: t('common.no'), value: 0 }
        ]
      }
    },
    {
      key: 'sort',
      label: t('system.dict.sort'),
      type: 'number',
      span: 12,
      props: { min: 0, max: 999 }
    },
    {
      key: 'options',
      label: t('system.dict.options'),
      type: 'select',
      span: 24,
      hidden: fieldFormData.value.fieldType !== 'enum',
      props: {
        placeholder: t('system.dict.optionsPlaceholder'),
        multiple: true,
        filterable: true,
        allowCreate: true,
        defaultFirstOption: true,
        options: (fieldFormData.value.options || []).map((option: string) => ({
          label: option,
          value: option
        }))
      }
    },
    {
      key: 'defaultValue',
      label: t('system.dict.defaultValue'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.dict.defaultValuePlaceholder'), maxlength: 255 }
    }
  ])

    /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      fieldName: [{ required: true, message: t('system.dict.fieldNamePlaceholder') }],
      fieldCode: [{ required: true, message: t('system.dict.fieldCodePlaceholder') }],
    }
  // 枚举类型必须填写候选项（与 handleSave 中的校验一致）
  if (fieldFormData.value.fieldType === 'enum') {
    rules.options = [{ required: true, message: t('system.dict.optionsPlaceholder') }]
  }
    return rules
  })
  const openFieldForm = (row?: Api.Dict.DictField) => {
    isFieldEdit.value = !!row
    editingFieldId.value = row?.id
    fieldFormData.value = row
      ? {
          fieldName: row.fieldName,
          fieldCode: row.fieldCode,
          fieldType: row.fieldType,
          isRequired: row.isRequired,
          isUnique: row.isUnique,
          sort: row.sort,
          options: row.options || [],
          defaultValue: row.defaultValue
        }
      : { fieldType: 'string', isRequired: 0, isUnique: 0, sort: 99, options: [] }
    fieldFormVisible.value = true
  }

  const handleFieldSubmit = async () => {
    if (!dict.value) return
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    const { fieldName, fieldCode, fieldType, options } = fieldFormData.value
    if (!fieldName?.trim()) {
      ElMessage.warning(t('system.dict.fieldNamePlaceholder'))
      return
    }
    if (!fieldCode?.trim()) {
      ElMessage.warning(t('system.dict.fieldCodePlaceholder'))
      return
    }
    if (fieldType === 'enum' && !options?.length) {
      ElMessage.warning(t('system.dict.optionsPlaceholder'))
      return
    }
    const payload: Api.Dict.DictFieldSaveParams = {
      id: editingFieldId.value,
      dictId: dict.value.id,
      fieldCode: fieldCode.trim(),
      fieldName: fieldName.trim(),
      fieldType,
      isRequired: fieldFormData.value.isRequired ?? 0,
      isUnique: fieldFormData.value.isUnique ?? 0,
      options: fieldType === 'enum' ? options : null,
      defaultValue: fieldFormData.value.defaultValue,
      sort: fieldFormData.value.sort ?? 0
    }
    if (isFieldEdit.value) {
      await fetchUpdateDictField(payload)
    } else {
      await fetchCreateDictField(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    fieldFormVisible.value = false
    loadFields()
  }

  defineExpose({ open })
</script>
