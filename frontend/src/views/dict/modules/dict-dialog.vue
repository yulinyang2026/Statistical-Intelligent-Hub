<!-- 字典新增 / 编辑弹窗（编码仅新增时可设） -->
<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="560px"
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
  import { fetchCreateDict, fetchUpdateDict } from '@/api/dict'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'DictDialog' })

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
  const editingId = ref<number>()
  const formData = ref<Record<string, any>>({})

  const dialogTitle = computed(() => (isEdit.value ? t('common.edit') : t('common.add')))

  const formItems = computed(() => [
    {
      key: 'name',
      label: t('system.dict.name'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.dict.namePlaceholder'), maxlength: 64 }
    },
    {
      key: 'code',
      label: t('system.dict.code'),
      type: 'input',
      span: 12,
      props: {
        placeholder: t('system.dict.codePlaceholder'),
        maxlength: 64,
        disabled: isEdit.value
      }
    },
    {
      key: 'description',
      label: t('system.dict.description'),
      type: 'input',
      span: 24,
      props: {
        placeholder: t('system.dict.descriptionPlaceholder'),
        maxlength: 255,
        type: 'textarea',
        rows: 2
      }
    },
    {
      key: 'sort',
      label: t('system.dict.sort'),
      type: 'number',
      span: 12,
      props: { min: 0, max: 999 }
    }
  ])

    /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      name: [{ required: true, message: t('system.dict.namePlaceholder') }],
      code: [{ required: true, message: t('system.dict.codePlaceholder') }],
    }
    return rules
  })
  const open = (row?: Api.Dict.DictListItem) => {
    isEdit.value = !!row
    editingId.value = row?.id
    formData.value = row
      ? {
          name: row.name,
          code: row.code,
          description: row.description,
          sort: row.sort
        }
      : { sort: 0 }
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = {}
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    const { name, code } = formData.value
    if (!name?.trim()) {
      ElMessage.warning(t('system.dict.namePlaceholder'))
      return
    }
    if (!code?.trim()) {
      ElMessage.warning(t('system.dict.codePlaceholder'))
      return
    }
    const payload: Api.Dict.DictSaveParams = {
      id: editingId.value,
      code: code.trim(),
      name: name.trim(),
      description: formData.value.description,
      sort: formData.value.sort ?? 0
    }
    if (isEdit.value) {
      await fetchUpdateDict(payload)
    } else {
      await fetchCreateDict(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
