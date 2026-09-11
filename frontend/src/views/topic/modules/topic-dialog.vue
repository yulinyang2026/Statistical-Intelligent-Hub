<!-- 专题新增 / 编辑弹窗（FR-TOP-004：状态字典化 + 组织部门/小组 + 人员姓名；
     与模块弹窗同构，差异：无「归属产品」「模块分类」两项） -->
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
  import { fetchCreateTopic, fetchTopicOptions, fetchUpdateTopic } from '@/api/topic'
  import { fetchUserOptions } from '@/api/user'
  import { fetchOrgTree } from '@/api/org'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'TopicDialog' })

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

  /** 筛选候选值（状态字典 + 组织树 + 现有负责人） */
  /** 负责部门/负责小组候选：组织管理（部门管理）的组织树 */
  const orgTree = ref<Api.Org.OrgTreeNode[]>([])

  const topicOptions = ref<Api.Topic.TopicOptions>({
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
      label: t('system.topic.title'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.topic.titlePlaceholder'), maxlength: 128 }
    },
    // 2026-09-11 用户需求：专题状态不再由表单填写——由任务待办自动流转（与模块同口径），
    // 新建默认非活跃，故此处移除该表单项
    {
      // 2026-09-11 需求：改组织树选择器（读组织管理的数据，可选任意层级节点）
      key: 'dept',
      label: t('system.topic.dept'),
      type: 'treeselect',
      span: 12,
      props: {
        placeholder: t('system.topic.deptPlaceholder'),
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
      label: t('system.topic.team'),
      type: 'treeselect',
      span: 12,
      props: {
        placeholder: t('system.topic.teamPlaceholder'),
        clearable: true,
        checkStrictly: true,
        data: orgTree.value,
        nodeKey: 'name',
        props: { label: 'name', children: 'children' }
      }
    },
    {
      key: 'owner',
      label: t('system.topic.owner'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.topic.ownerPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'rd',
      label: t('system.topic.rd'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.topic.rdPlaceholder'),
        clearable: true,
        filterable: true,
        multiple: true,
        options: userOptions.value
      }
    },
    {
      key: 'test',
      label: t('system.topic.test'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.topic.testPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      key: 'cost',
      label: t('system.topic.cost'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.topic.costPlaceholder'), maxlength: 64 }
    },
    {
      key: 'desc',
      label: t('system.topic.desc'),
      type: 'input',
      span: 24,
      props: { placeholder: t('system.topic.descPlaceholder'), maxlength: 500, type: 'textarea', rows: 3 }
    }
  ])

  /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      name: [{ required: true, message: t('system.topic.titlePlaceholder') }],
      dept: [{ required: true, message: t('system.topic.deptPlaceholder') }],
      team: [{ required: true, message: t('system.topic.teamPlaceholder') }],
      owner: [{ required: true, message: t('system.topic.ownerPlaceholder') }],
    }
    return rules
  })
  const open = async (row?: Api.Topic.TopicListItem) => {
    isEdit.value = !!row
    editingId.value = row?.id
    const [opts, users, orgs] = await Promise.all([fetchTopicOptions(), fetchUserOptions(), fetchOrgTree()])
    topicOptions.value = opts
    orgTree.value = orgs
    userOptions.value = users.map((user) => ({ label: user.name, value: user.name }))
    formData.value = row
      ? {
          name: row.name,
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
    const { name, dept, team, owner } = formData.value
    if (!name?.trim()) {
      ElMessage.warning(t('system.topic.titlePlaceholder'))
      return
    }
    for (const [field, value] of [
      ['dept', dept],
      ['team', team],
      ['owner', owner]
    ] as const) {
      if (!value) {
        ElMessage.warning(t(`system.topic.${field}Placeholder`))
        return
      }
    }
    const payload: Api.Topic.TopicSaveParams = {
      id: editingId.value,
      name: name.trim(),
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
      await fetchUpdateTopic(editingId.value, payload)
    } else {
      await fetchCreateTopic(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
