<!-- 组织新增 / 编辑弹窗（编辑时切换上级部门即移动） -->
<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="600px"
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
  import { fetchCreateOrg, fetchOrgTree, fetchUpdateOrg } from '@/api/org'
  import { fetchUserOptions } from '@/api/user'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'OrgDialog' })

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

  /** 上级部门树（带虚拟根节点，parentId = 0 表示顶级） */
  const parentTree = ref<Record<string, any>[]>([])
  /** 负责人下拉选项 */
  const leaderOptions = ref<{ label: string; value: number }[]>([])

  const dialogTitle = computed(() => (isEdit.value ? t('common.edit') : t('common.add')))

  const formItems = computed(() => [
    {
      key: 'parentId',
      label: t('system.org.parentId'),
      type: 'treeselect',
      span: 24,
      props: {
        data: parentTree.value,
        nodeKey: 'id',
        props: { label: 'name', children: 'children' },
        placeholder: t('system.org.parentPlaceholder'),
        clearable: true,
        // 2026-09-10 用户需求：上级部门可选任意节点（含总公司），此前父节点点选仅展开不选中
        checkStrictly: true
      }
    },
    {
      key: 'name',
      label: t('system.org.name'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.org.namePlaceholder'), maxlength: 50 }
    },
    {
      key: 'code',
      label: t('system.org.code'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.org.codePlaceholder'), maxlength: 50 }
    },
    {
      key: 'leaderUserId',
      label: t('system.org.leader'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.org.leaderPlaceholder'),
        clearable: true,
        filterable: true,
        options: leaderOptions.value
      }
    },
    {
      key: 'sort',
      label: t('system.org.sort'),
      type: 'number',
      span: 12,
      props: { min: 0, max: 999 }
    },
    {
      key: 'status',
      label: t('common.status'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('common.statusPlaceholder'),
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
      name: [{ required: true, message: t('system.org.namePlaceholder') }],
      code: [{ required: true, message: t('system.org.codePlaceholder') }],
    }
    return rules
  })
  /** 预设上级机构（组织管理页在选中某机构时新增下级，2026-09-11） */
  const presetParentId = ref<number>()

  const open = async (row?: Api.Org.OrgTreeNode, parentId?: number) => {
    isEdit.value = !!row
    editingId.value = row?.id
    presetParentId.value = row ? undefined : parentId
    formData.value = row
      ? {
          parentId: row.parentId,
          name: row.name,
          code: row.code,
          leaderUserId: row.leaderUserId,
          sort: row.sort,
          status: row.status
        }
      : { parentId: presetParentId.value ?? 0, sort: 0, status: 1 }
    const [tree, users] = await Promise.all([fetchOrgTree(), fetchUserOptions()])
    parentTree.value = [{ id: 0, name: t('system.org.rootName'), children: tree }]
    leaderOptions.value = users.map((user) => ({ label: user.name, value: user.id }))
    visible.value = true
  }

  const handleClosed = () => {
    presetParentId.value = undefined
    formData.value = {}
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    const { name, code } = formData.value
    if (!name?.trim()) {
      ElMessage.warning(t('system.org.namePlaceholder'))
      return
    }
    if (!code?.trim()) {
      ElMessage.warning(t('system.org.codePlaceholder'))
      return
    }
    const payload: Api.Org.OrgSaveParams = {
      parentId: formData.value.parentId ?? 0,
      name,
      code,
      leaderUserId: formData.value.leaderUserId ?? null,
      sort: formData.value.sort ?? 0,
      status: formData.value.status ?? 1
    }
    if (isEdit.value) {
      await fetchUpdateOrg({ ...payload, id: editingId.value })
    } else {
      await fetchCreateOrg(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
