<!-- 人员批量转移弹窗（源部门全部人员转移到目标部门） -->
<template>
  <ElDialog
    v-model="visible"
    :title="t('system.org.transfer')"
    width="480px"
    :close-on-click-modal="false"
    append-to-body
    @closed="handleClosed"
  >
    <ElAlert
      type="warning"
      :closable="false"
      show-icon
      :title="t('system.org.transferTips')"
      class="mb-4"
    />
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
  import { fetchOrgTree, fetchTransferOrgUsers } from '@/api/org'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'TransferDialog' })

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

  const sourceOrg = ref<Api.Org.OrgTreeNode>()
  const formData = ref<Record<string, any>>({})

  /** 目标部门树（剔除源部门，避免转移到自身） */
  const targetTree = ref<Api.Org.OrgTreeNode[]>([])

  const formItems = computed(() => [
    {
      key: 'fromOrg',
      label: t('system.org.fromOrg'),
      type: 'input',
      span: 24,
      props: { disabled: true }
    },
    {
      key: 'toOrgId',
      label: t('system.org.toOrg'),
      type: 'treeselect',
      span: 24,
      props: {
        data: targetTree.value,
        nodeKey: 'id',
        props: { label: 'name', children: 'children' },
        placeholder: t('system.org.toOrgPlaceholder'),
        clearable: true,
        // 2026-09-10 用户需求：目标部门可选任意节点（含上级部门）
        checkStrictly: true
      }
    }
  ])

  /** 递归剔除指定部门节点 */
  const excludeOrg = (nodes: Api.Org.OrgTreeNode[], excludeId: number): Api.Org.OrgTreeNode[] =>
    nodes
      .filter((node) => node.id !== excludeId)
      .map((node) => ({ ...node, children: excludeOrg(node.children || [], excludeId) }))

    /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      toOrgId: [{ required: true, message: t('system.org.toOrgPlaceholder') }],
    }
    return rules
  })
  const open = async (row: Api.Org.OrgTreeNode) => {
    sourceOrg.value = row
    formData.value = { fromOrg: row.name, toOrgId: undefined }
    const tree = await fetchOrgTree()
    targetTree.value = excludeOrg(tree, row.id)
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = {}
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    if (!formData.value.toOrgId) {
      ElMessage.warning(t('system.org.toOrgPlaceholder'))
      return
    }
    if (!sourceOrg.value) return
    await fetchTransferOrgUsers({
      fromOrgId: sourceOrg.value.id,
      toOrgId: formData.value.toOrgId
    })
    ElMessage.success(t('common.operateSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
