<!-- 子系统新增 / 编辑弹窗（FR-PROJ-013：所属项目、名称*、负责人、排序 + 2026-09-11 扩字段；
     列表页与详情「子系统」Tab 共用）
     2026-09-11 用户需求：① 全部属性由单列（span 24）改为两列（span 12），弹窗宽度 520→760 与项目弹窗对齐；
     ② 成本对象字段下线（表单不再出现，编辑时按原值回传以免覆盖存量）。 -->
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
  import { fetchCreateSubsystem, fetchProjectOptions, fetchProjectPage, fetchUpdateSubsystem } from '@/api/project'
  import { fetchUserOptions } from '@/api/user'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'SubsystemDialog' })

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
  /** 预设所属项目（详情页进入时锁定，不可改） */
  const presetProjectId = ref<string>()
  const formData = ref<Record<string, any>>({})
  /** 成本对象已从表单下线（2026-09-11 用户需求）：编辑时按原值回传，避免覆盖存量数据 */
  const editingCost = ref<string | null>(null)

  /** 所属项目下拉 */
  const projectOptions = ref<{ label: string; value: string }[]>([])
  /** 字典/组织候选（2026-09-11 用户需求：子系统表单扩字段） */
  const dictOptions = ref<Api.Project.ProjectOptions>({
    products: [],
    statuses: [],
    levels: [],
    depts: [],
    teams: [],
    owners: []
  })
  /** 人员下拉（负责人） */
  const userOptions = ref<{ label: string; value: string }[]>([])

  const dialogTitle = computed(() => (isEdit.value ? t('common.edit') : t('common.add')))

  const formItems = computed(() => [
    {
      key: 'project',
      label: t('system.project.subsystemProject'),
      type: 'select',
      span: 12,
      hidden: !!presetProjectId.value,
      props: {
        placeholder: t('system.project.subsystemProjectPlaceholder'),
        filterable: true,
        options: projectOptions.value
      }
    },
    {
      key: 'name',
      label: t('system.project.subsystemName'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.project.subsystemNamePlaceholder'), maxlength: 128 }
    },
    {
      key: 'owner',
      label: t('system.project.subsystemOwner'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.subsystemOwnerPlaceholder'),
        clearable: true,
        filterable: true,
        options: userOptions.value
      }
    },
    {
      // 2026-09-11 用户需求：子系统表单扩字段（与项目一致）
      // 2026-09-11 再修订：成本对象字段下线（表单与列表/详情列一并去掉，数据字段保留兼容）
      key: 'product',
      label: t('system.project.product'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.productPlaceholder'),
        clearable: true,
        options: nameOptions(dictOptions.value.products)
      }
    },
    {
      key: 'dept',
      label: t('system.project.dept'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.deptPlaceholder'),
        clearable: true,
        options: nameOptions(dictOptions.value.depts)
      }
    },
    {
      key: 'team',
      label: t('system.project.team'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.teamPlaceholder'),
        clearable: true,
        options: nameOptions(dictOptions.value.teams)
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
      key: 'status',
      label: t('system.project.status'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.project.statusPlaceholder'),
        clearable: true,
        options: nameOptions(dictOptions.value.statuses)
      }
    },
    {
      key: 'sort',
      label: t('system.project.subsystemSort'),
      type: 'number',
      span: 12,
      props: { placeholder: t('system.project.subsystemSortPlaceholder'), min: 0, max: 9999 }
    }
  ])

  const nameOptions = (values: string[]) => values.map((value) => ({ label: value, value }))

  /** options.project 已指定项目时（从列表/详情某项目进入） */
  /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      name: [{ required: true, message: t('system.project.subsystemNamePlaceholder') }],
    }
  // 所属项目：详情页进入时锁定并隐藏该表单项，此时无需必填标记
  if (!presetProjectId.value) {
    rules.project = [{ required: true, message: t('system.project.subsystemProjectPlaceholder') }]
  }
    return rules
  })
  const open = async (options?: {
    row?: Api.Project.SubsystemItem
    project?: { id: string; name: string }
    /** 新增时按所属项目预填（2026-09-11：子系统字段与项目保持一致） */
    presetProject?: Api.Project.ProjectListItem | Api.Project.ProjectDetail
  }) => {
    const row = options?.row
    isEdit.value = !!row
    editingId.value = row?.id
    presetProjectId.value = row ? undefined : options?.project?.id
    editingCost.value = row?.cost ?? null

    const [users, page, dicts] = await Promise.all([
      fetchUserOptions(),
      fetchProjectPage({ current: 1, size: 0 }),
      fetchProjectOptions()
    ])
    userOptions.value = users.map((user) => ({ label: user.name, value: user.name }))
    projectOptions.value = page.records.map((project) => ({ label: project.name, value: project.id }))
    dictOptions.value = dicts
    const preset = options?.presetProject

    if (row) {
      formData.value = {
        project: row.projectId,
        name: row.name,
        owner: row.owner ?? undefined,
        sort: row.sort ?? 0,
        product: row.product ?? undefined,
        dept: row.dept ?? undefined,
        team: row.team ?? undefined,
        rd: row.rd ?? undefined,
        pm: row.pm ?? undefined,
        test: row.test ?? undefined,
        status: row.status ?? undefined
      }
    } else {
      // 详情页进入时锁定项目，不进入下拉
      if (options?.project) {
        projectOptions.value = [
          { label: options.project.name, value: options.project.id },
          ...projectOptions.value
        ]
      }
      formData.value = {
        project: options?.project?.id ?? '',
        owner: preset?.owner ?? undefined,
        sort: 0,
        // 预填所属项目的同名字段（成本对象已下线，不再预填）
        product: preset?.product ?? undefined,
        dept: preset?.dept ?? undefined,
        team: preset?.team ?? undefined,
        rd: preset?.rd ?? undefined,
        pm: preset?.pm ?? undefined,
        test: preset?.test ?? undefined,
        status: preset?.status ?? undefined
      }
    }
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = {}
    presetProjectId.value = undefined
    editingCost.value = null
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    const projectId = presetProjectId.value ?? (formData.value.project as string | undefined)
    const name = formData.value.name as string | undefined
    if (!projectId) {
      ElMessage.warning(t('system.project.subsystemProjectPlaceholder'))
      return
    }
    if (!name?.trim()) {
      ElMessage.warning(t('system.project.subsystemNamePlaceholder'))
      return
    }
    const payload: Api.Project.SubsystemSaveParams = {
      id: editingId.value,
      projectId,
      name: name.trim(),
      owner: formData.value.owner ?? null,
      sort: formData.value.sort ?? 0,
      // 表单已不含成本对象：编辑按原值回传，新增留空
      cost: editingCost.value,
      product: formData.value.product ?? null,
      dept: formData.value.dept ?? null,
      team: formData.value.team ?? null,
      rd: formData.value.rd ?? null,
      pm: formData.value.pm ?? null,
      test: formData.value.test ?? null,
      status: formData.value.status ?? null
    }
    if (isEdit.value && editingId.value) {
      await fetchUpdateSubsystem(editingId.value, payload)
    } else {
      await fetchCreateSubsystem(projectId, payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
