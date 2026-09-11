<!-- 项目新增 / 编辑弹窗（FR-PROJ-004：字典化产品/状态 + 级别 + 组织部门/小组 + 人员姓名 + 挂靠模块/专题） -->
<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="760px"
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
  import { fetchCreateProject, fetchProjectOptions, fetchUpdateProject } from '@/api/project'
  import { fetchUserOptions } from '@/api/user'
  import { fetchOrgTree } from '@/api/org'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'ProjectDialog' })

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

  /** 筛选候选值（字典 + 组织树 + 挂靠对象） */
  /** 负责部门/负责小组候选：组织管理（部门管理）的组织树 */
  const orgTree = ref<Api.Org.OrgTreeNode[]>([])

  const projectOptions = ref<Api.Project.ProjectOptions>({
    products: [],
    statuses: [],
    levels: [],
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
      label: t('system.project.title'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.project.titlePlaceholder'), maxlength: 128 }
    },
    {
      key: 'cost',
      label: t('system.project.cost'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.project.costPlaceholder'), maxlength: 64 }
    },
    {
      key: 'level',
      label: t('system.project.level'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.levelPlaceholder'),
        clearable: true,
        options: nameOptions(projectOptions.value.levels)
      }
    },
    {
      key: 'product',
      label: t('system.project.product'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.productPlaceholder'),
        clearable: true,
        options: nameOptions(projectOptions.value.products)
      }
    },
    {
      key: 'productLine',
      label: t('system.project.productLine'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.project.productLinePlaceholder'), maxlength: 64 }
    },
    {
      key: 'status',
      label: t('system.project.status'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.statusPlaceholder'),
        options: nameOptions(projectOptions.value.statuses)
      }
    },
    {
      // 2026-09-11 需求：改组织树选择器（读组织管理的数据，可选任意层级节点）
      key: 'dept',
      label: t('system.project.dept'),
      type: 'treeselect',
      span: 12,
      props: {
        placeholder: t('system.project.deptPlaceholder'),
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
      label: t('system.project.team'),
      type: 'treeselect',
      span: 12,
      props: {
        placeholder: t('system.project.teamPlaceholder'),
        clearable: true,
        checkStrictly: true,
        data: orgTree.value,
        nodeKey: 'name',
        props: { label: 'name', children: 'children' }
      }
    },
    {
      key: 'owner',
      label: t('system.project.owner'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.ownerPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'pm',
      label: t('system.project.pm'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.pmPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'sale',
      label: t('system.project.sale'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.salePlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'rd',
      label: t('system.project.rd'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.rdPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'test',
      label: t('system.project.test'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.testPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'budget',
      label: t('system.project.budget'),
      type: 'number',
      span: 12,
      props: { placeholder: t('system.project.budgetPlaceholder'), min: 0, precision: 2 }
    },
    {
      // 2026-09-11 用户需求：开始/结束日期合并为「项目周期」（区间控件）
      key: 'dateRange',
      label: t('system.project.dateRange'),
      type: 'date',
      span: 12,
      props: {
        placeholder: t('system.project.dateRangePlaceholder'),
        type: 'daterange',
        valueFormat: 'YYYY-MM-DD',
        format: 'YYYY-MM-DD',
        rangeSeparator: '~'
      }
    },
    {
      key: 'stakeholders',
      label: t('system.project.stakeholders'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.stakeholdersPlaceholder'),
        clearable: true,
        filterable: true,
        multiple: true,
        options: userOptions.value
      }
    },
    {
      key: 'desc',
      label: t('system.project.desc'),
      type: 'input',
      span: 24,
      props: { placeholder: t('system.project.descPlaceholder'), maxlength: 500, type: 'textarea', rows: 3 }
    }
  ])

  /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      name: [{ required: true, message: t('system.project.titlePlaceholder') }],
      cost: [{ required: true, message: t('system.project.costPlaceholder') }],
      level: [{ required: true, message: t('system.project.levelPlaceholder') }],
      product: [{ required: true, message: t('system.project.productPlaceholder') }],
      status: [{ required: true, message: t('system.project.statusPlaceholder') }],
      dept: [{ required: true, message: t('system.project.deptPlaceholder') }],
      team: [{ required: true, message: t('system.project.teamPlaceholder') }],
      owner: [{ required: true, message: t('system.project.ownerPlaceholder') }],
    }
    return rules
  })
  const open = async (row?: Api.Project.ProjectListItem) => {
    isEdit.value = !!row
    editingId.value = row?.id
    const [opts, users, orgs] = await Promise.all([fetchProjectOptions(), fetchUserOptions(), fetchOrgTree()])
    projectOptions.value = opts
    orgTree.value = orgs
    userOptions.value = users.map((user) => ({ label: user.name, value: user.name }))
    formData.value = row
      ? {
          name: row.name,
          cost: row.cost,
          level: row.level,
          product: row.product,
          productLine: row.productLine ?? undefined,
          status: row.status,
          dept: row.dept,
          team: row.team,
          owner: row.owner,
          pm: row.pm ?? undefined,
          sale: row.sale ?? undefined,
          rd: row.rd ?? undefined,
          test: row.test ?? undefined,
          budget: row.budget ?? undefined,
          dateRange: row.startDate && row.endDate ? [row.startDate, row.endDate] : undefined,
          stakeholders: row.stakeholders ?? [],
          desc: row.desc ?? undefined
        }
      : { stakeholders: [] }
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = {}
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    const { name, cost, level, product, status, dept, team, owner } = formData.value
    if (!name?.trim()) {
      ElMessage.warning(t('system.project.titlePlaceholder'))
      return
    }
    for (const [field, value] of [
      ['cost', cost],
      ['level', level],
      ['product', product],
      ['status', status],
      ['dept', dept],
      ['team', team],
      ['owner', owner]
    ] as const) {
      if (!value) {
        ElMessage.warning(t(`system.project.${field}Placeholder`))
        return
      }
    }
    const dateRange = formData.value.dateRange as string[] | undefined
    const payload: Api.Project.ProjectSaveParams = {
      id: editingId.value,
      name: name.trim(),
      cost: cost.trim(),
      level,
      product,
      productLine: formData.value.productLine ?? null,
      status,
      dept,
      team,
      owner,
      pm: formData.value.pm ?? null,
      sale: formData.value.sale ?? null,
      rd: formData.value.rd ?? null,
      test: formData.value.test ?? null,
      budget: formData.value.budget ?? null,
      startDate: dateRange?.[0] ?? null,
      endDate: dateRange?.[1] ?? null,
      stakeholders: formData.value.stakeholders ?? [],
      desc: formData.value.desc ?? null
    }
    if (isEdit.value && editingId.value) {
      await fetchUpdateProject(editingId.value, payload)
    } else {
      await fetchCreateProject(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
