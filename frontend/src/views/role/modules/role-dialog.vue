<!-- 角色新增 / 编辑弹窗（内置角色名称与编码不可改） -->
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
  import { fetchCreateRole, fetchUpdateRole } from '@/api/role'
  import { fetchOrgTree } from '@/api/org'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'RoleDialog' })

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
  const isBuiltin = ref(false)
  const editingId = ref<number>()
  const formData = ref<Record<string, any>>({})

  /** 自定义部门范围树（数据范围 = 自定义部门时展示） */
  const orgTree = ref<Api.Org.OrgTreeNode[]>([])

  const dialogTitle = computed(() => (isEdit.value ? t('common.edit') : t('common.add')))

  const dataScopeOptions = computed(() => [
    { label: t('system.role.scopeAll'), value: 1 },
    { label: t('system.role.scopeOrgAndChildren'), value: 2 },
    { label: t('system.role.scopeOrgOnly'), value: 3 },
    { label: t('system.role.scopeSelf'), value: 4 },
    { label: t('system.role.scopeSelfAndSubordinates'), value: 5 },
    { label: t('system.role.scopeCustom'), value: 6 }
  ])

  const formItems = computed(() => [
    {
      key: 'name',
      label: t('system.role.name'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.role.namePlaceholder'), maxlength: 50, disabled: isEdit.value && isBuiltin.value }
    },
    {
      key: 'code',
      label: t('system.role.code'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.role.codePlaceholder'), maxlength: 50, disabled: isEdit.value && isBuiltin.value }
    },
    {
      key: 'dataScope',
      label: t('system.role.dataScope'),
      type: 'select',
      span: 12,
      props: { placeholder: t('system.role.dataScopePlaceholder'), options: dataScopeOptions.value }
    },
    {
      key: 'customOrgIds',
      label: t('system.role.customOrgIds'),
      type: 'treeselect',
      span: 12,
      hidden: formData.value.dataScope !== 6,
      props: {
        data: orgTree.value,
        nodeKey: 'id',
        props: { label: 'name', children: 'children' },
        placeholder: t('system.role.customOrgIdsPlaceholder'),
        clearable: true,
        multiple: true,
        checkStrictly: true
      }
    },
    {
      key: 'remark',
      label: t('system.role.remark'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.role.remarkPlaceholder'), maxlength: 200 }
    },
    {
      key: 'status',
      label: t('common.status'),
      type: 'select',
      span: 12,
      props: {
        options: [
          { label: t('common.enable'), value: 1 },
          { label: t('common.disable'), value: 0 }
        ]
      }
    }
  ])

  /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      name: [{ required: true, message: t('system.role.namePlaceholder') }],
      code: [{ required: true, message: t('system.role.codePlaceholder') }],
    }
    return rules
  })
  const open = async (row?: Api.Role.RoleListItem) => {
    isEdit.value = !!row
    isBuiltin.value = row?.type === 1
    editingId.value = row?.id
    orgTree.value = await fetchOrgTree()
    formData.value = row
      ? {
          name: row.name,
          code: row.code,
          dataScope: row.dataScope,
          customOrgIds: row.customOrgIds || [],
          remark: row.remark,
          status: row.status
        }
      : { dataScope: 1, customOrgIds: [], status: 1 }
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
      ElMessage.warning(t('system.role.namePlaceholder'))
      return
    }
    if (!code?.trim()) {
      ElMessage.warning(t('system.role.codePlaceholder'))
      return
    }
    const payload: Api.Role.RoleSaveParams = {
      id: editingId.value,
      name,
      code,
      dataScope: formData.value.dataScope ?? 1,
      customOrgIds: formData.value.dataScope === 6 ? formData.value.customOrgIds || [] : null,
      remark: formData.value.remark,
      status: formData.value.status ?? 1
    }
    if (isEdit.value) {
      await fetchUpdateRole(payload)
    } else {
      await fetchCreateRole(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
