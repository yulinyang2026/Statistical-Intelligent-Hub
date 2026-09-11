<!-- 模块新增 / 编辑弹窗（FR-MOD-004：字典化产品/分类/状态 + 组织部门/小组 + 人员姓名） -->
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
  import { fetchCreateModule, fetchModuleOptions, fetchUpdateModule } from '@/api/module'
  import { fetchUserOptions } from '@/api/user'
  import { fetchOrgTree } from '@/api/org'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'ModuleDialog' })

  const emit = defineEmits<{ success: [] }>()

  const { t } = useI18n()

  const visible = defineModel<boolean>('visible', { default: false })
  const formRef = useTemplateRef<{
    validate: () => Promise<boolean>
    reset: () => void
    getOutput: () => Record<string, any>
  }>('formRef')

  const isEdit = ref(false)
  const editingId = ref<string>()
  const formData = ref<Record<string, any>>({})

  /** 筛选候选值（字典 + 组织树 + 现有负责人） */
  /** 负责部门/负责小组候选：组织管理（部门管理）的组织树 */
  const orgTree = ref<Api.Org.OrgTreeNode[]>([])

  const moduleOptions = ref<Api.Module.ModuleOptions>({
    products: [],
    categories: [],
    statuses: [],
    depts: [],
    teams: [],
    owners: []
  })
  /** 人员下拉（姓名，与 User.name 关联） */
  const userOptions = ref<{ label: string; value: string }[]>([])

  const dialogTitle = computed(() => (isEdit.value ? t('common.edit') : t('common.add')))

  const nameOptions = (values: string[]) => values.map((value) => ({ label: value, value }))

  const formItems = computed(() => [
    {
      key: 'name',
      label: t('system.module.title'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.module.titlePlaceholder'), maxlength: 128 }
    },
    {
      key: 'product',
      label: t('system.module.product'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.module.productPlaceholder'),
        clearable: true,
        options: nameOptions(moduleOptions.value.products)
      }
    },
    {
      key: 'category',
      label: t('system.module.category'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.module.categoryPlaceholder'),
        clearable: true,
        options: nameOptions(moduleOptions.value.categories)
      }
    },
    // 2026-09-11 用户需求：模块状态不再由表单填写——由任务待办自动流转
    // （待办>0 → 活跃；=0 → 非活跃），新建默认非活跃，故此处移除该表单项
    {
      // 2026-09-11 需求：改组织树选择器（读组织管理的数据，可选任意层级节点）
      key: 'dept',
      label: t('system.module.dept'),
      type: 'treeselect',
      span: 12,
      props: {
        placeholder: t('system.module.deptPlaceholder'),
        clearable: true,
        checkStrictly: true,
        data: orgTree.value,
        nodeKey: 'name',
        props: { label: 'name', children: 'children' }
      }
    },
    {
      // 2026-09-11 需求：改组织树选择器（读组织管理的数据，可选任意层级节点）
      key: 'team',
      label: t('system.module.team'),
      type: 'treeselect',
      span: 12,
      props: {
        placeholder: t('system.module.teamPlaceholder'),
        clearable: true,
        checkStrictly: true,
        data: orgTree.value,
        nodeKey: 'name',
        props: { label: 'name', children: 'children' }
      }
    },
    {
      key: 'owner',
      label: t('system.module.owner'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.module.ownerPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'rd',
      label: t('system.module.rd'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.module.rdPlaceholder'),
        clearable: true,
        filterable: true,
        multiple: true,
        options: userOptions.value
      }
    },
    {
      key: 'test',
      label: t('system.module.test'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.module.testPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'cost',
      label: t('system.module.cost'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.module.costPlaceholder'), maxlength: 64 }
    },
    {
      key: 'desc',
      label: t('system.module.desc'),
      type: 'input',
      span: 24,
      props: { placeholder: t('system.module.descPlaceholder'), maxlength: 500, type: 'textarea', rows: 3 }
    }
  ])

  /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      name: [{ required: true, message: t('system.module.titlePlaceholder') }],
      product: [{ required: true, message: t('system.module.productPlaceholder') }],
      category: [{ required: true, message: t('system.module.categoryPlaceholder') }],
      dept: [{ required: true, message: t('system.module.deptPlaceholder') }],
      team: [{ required: true, message: t('system.module.teamPlaceholder') }],
      owner: [{ required: true, message: t('system.module.ownerPlaceholder') }],
    }
    return rules
  })
  const open = async (row?: Api.Module.ModuleListItem) => {
    isEdit.value = !!row
    editingId.value = row?.id
    const [opts, users, orgs] = await Promise.all([fetchModuleOptions(), fetchUserOptions(), fetchOrgTree()])
    moduleOptions.value = opts
    orgTree.value = orgs
    userOptions.value = users.map((user) => ({ label: user.name, value: user.name }))
    formData.value = row
      ? {
          name: row.name,
          product: row.product,
          category: row.category,
          dept: row.dept,
          team: row.team,
          owner: row.owner,
          rd: row.rd ?? [],
          test: row.test ?? undefined,
          cost: row.cost ?? undefined,
          desc: row.desc ?? undefined
        }
      : { rd: [] }
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = {}
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    const { name, product, category, dept, team, owner } = formData.value
    if (!name?.trim()) {
      ElMessage.warning(t('system.module.titlePlaceholder'))
      return
    }
    for (const [field, value] of [
      ['product', product],
      ['category', category],
      ['dept', dept],
      ['team', team],
      ['owner', owner]
    ] as const) {
      if (!value) {
        ElMessage.warning(t(`system.module.${field}Placeholder`))
        return
      }
    }
    const payload: Api.Module.ModuleSaveParams = {
      id: editingId.value,
      name: name.trim(),
      product,
      category,
      dept,
      team,
      owner,
      rd: formData.value.rd ?? [],
      test: formData.value.test ?? null,
      cost: formData.value.cost ?? null,
      stakeholders: [],
      desc: formData.value.desc ?? null
    }
    if (isEdit.value && editingId.value) {
      await fetchUpdateModule(editingId.value, payload)
    } else {
      await fetchCreateModule(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
