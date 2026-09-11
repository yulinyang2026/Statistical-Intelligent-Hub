<!-- 用户新增 / 编辑弹窗（人员档案 + 账号 + 部门归属 + 角色分配；修改时密码留空不变更）
     2026-09-11 用户需求：从「组织管理」页新增时会带上所属机构，并要求选择「身份」——
     机构管理者（同时成为该机构负责人）/ 下级成员（直属主管默认为该机构负责人） -->
<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="640px"
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
  import { fetchCreateUser, fetchUpdateUser, fetchUserDetail, fetchUserOptions } from '@/api/user'
  import { fetchOrgTree } from '@/api/org'
  import { fetchRoleList } from '@/api/role'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'UserDialog' })

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
  /** 组织管理页带入的所属机构（新增时锁定主属机构并显示「身份」，2026-09-11 用户需求） */
  const presetOrg = ref<{ id: number; name: string }>()

  /** 部门树（主属/兼职共用） */
  const orgTree = ref<Api.Org.OrgTreeNode[]>([])
  /** 直属主管下拉选项（编辑时排除自身） */
  const managerOptions = ref<{ label: string; value: number }[]>([])
  /** 角色多选选项 */
  const roleOptions = ref<{ label: string; value: number }[]>([])

  const dialogTitle = computed(() => (isEdit.value ? t('common.edit') : t('common.add')))

  const formItems = computed(() => [
    {
      key: 'name',
      label: t('system.user.name'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.user.namePlaceholder'), maxlength: 50 }
    },
    {
      key: 'employeeNo',
      label: t('system.user.employeeNo'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.user.employeeNoPlaceholder'), maxlength: 50 }
    },
    // 2026-09-11 用户需求：新增用户去掉手机号字段（后端已改为选填，列表仍展示存量值）
    {
      key: 'email',
      label: t('system.user.email'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.user.emailPlaceholder'), maxlength: 100 }
    },
    {
      key: 'username',
      label: t('system.user.username'),
      type: 'input',
      span: 12,
      props: { placeholder: t('system.user.usernamePlaceholder'), maxlength: 50 }
    },
    {
      key: 'password',
      label: t('system.user.password'),
      type: 'input',
      span: 12,
      props: {
        placeholder: isEdit.value
          ? t('system.user.passwordEditPlaceholder')
          : t('system.user.passwordPlaceholder'),
        type: 'password',
        showPassword: true
      }
    },
    {
      key: 'manager',
      label: t('system.user.manager'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.user.managerPlaceholder'),
        clearable: true,
        filterable: true,
        options: managerOptions.value
      }
    },
    {
      // 2026-09-11 用户需求：组织管理页新增用户时的「创建属性」
      key: 'orgRole',
      label: t('system.user.orgRole'),
      type: 'select',
      span: 12,
      hidden: !(presetOrg.value && !isEdit.value),
      props: {
        placeholder: t('system.user.orgRolePlaceholder'),
        options: [
          { label: t('system.user.orgRoleOrgLeader'), value: 'leader' },
          { label: t('system.user.orgRoleMember'), value: 'member' }
        ]
      }
    },
    {
      key: 'primaryOrg',
      label: t('system.user.primaryOrg'),
      type: 'treeselect',
      span: 12,
      props: {
        data: orgTree.value,
        nodeKey: 'id',
        props: { label: 'name', children: 'children' },
        placeholder: t('system.user.primaryOrgPlaceholder'),
        clearable: true,
        checkStrictly: true
      }
    },
    {
      key: 'secondaryOrgs',
      label: t('system.user.secondaryOrgs'),
      type: 'treeselect',
      span: 12,
      props: {
        data: orgTree.value,
        nodeKey: 'id',
        props: { label: 'name', children: 'children' },
        placeholder: t('system.user.secondaryOrgsPlaceholder'),
        clearable: true,
        multiple: true,
        checkStrictly: true
      }
    },
    {
      key: 'role',
      label: t('system.user.role'),
      type: 'select',
      span: 12,
      props: {
        placeholder: t('system.user.rolePlaceholder'),
        clearable: true,
        multiple: true,
        options: roleOptions.value
      }
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
      name: [{ required: true, message: t('system.user.namePlaceholder') }],
      employeeNo: [{ required: true, message: t('system.user.employeeNoPlaceholder') }],
      username: [{ required: true, message: t('system.user.usernamePlaceholder') }],
      primaryOrg: [{ required: true, message: t('system.user.primaryOrgPlaceholder') }],
    }
  // 初始密码仅新增时必填（编辑留空表示不修改）
  if (!isEdit.value) {
    rules.password = [{ required: true, message: t('system.user.passwordPlaceholder') }]
  }
  // 身份：仅组织管理页新增时出现且必填
  if (presetOrg.value && !isEdit.value) {
    rules.orgRole = [{ required: true, message: t('system.user.orgRolePlaceholder') }]
  }
    return rules
  })
  const open = async (row?: Api.User.UserListItem, org?: { id: number; name: string }) => {
    isEdit.value = !!row
    editingId.value = row?.id
    presetOrg.value = row ? undefined : org
    const [tree, managers, roles] = await Promise.all([
      fetchOrgTree(),
      fetchUserOptions({ excludeUserId: row?.id }),
      fetchRoleList()
    ])
    orgTree.value = tree
    managerOptions.value = managers.map((user) => ({ label: user.name, value: user.id }))
    roleOptions.value = roles.map((role) => ({ label: role.name, value: role.id }))

    if (row) {
      const detail = await fetchUserDetail({ id: row.id })
      formData.value = {
        name: detail.name,
        employeeNo: detail.employeeNo,
        mobile: detail.mobile, // 表单已不展示：仅用于编辑时原样回传，避免覆盖存量手机号
        email: detail.email,
        username: detail.username,
        password: '',
        manager: detail.managerUserId,
        primaryOrg: detail.primaryOrgId,
        secondaryOrgs: detail.orgIds.filter((id) => id !== detail.primaryOrgId),
        role: detail.roleIds,
        status: detail.status
      }
    } else {
      formData.value = {
        password: '',
        secondaryOrgs: [],
        role: [],
        status: 1,
        primaryOrg: presetOrg.value?.id,
        orgRole: presetOrg.value ? 'member' : undefined
      }
    }
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = {}
    presetOrg.value = undefined
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    const { name, username, password, primaryOrg } = formData.value
    if (!name?.trim()) {
      ElMessage.warning(t('system.user.namePlaceholder'))
      return
    }
    if (!username?.trim()) {
      ElMessage.warning(t('system.user.usernamePlaceholder'))
      return
    }
    if (!isEdit.value && !password) {
      ElMessage.warning(t('system.user.passwordPlaceholder'))
      return
    }
    if (!primaryOrg) {
      ElMessage.warning(t('system.user.primaryOrgPlaceholder'))
      return
    }
    // 兼职部门剔除主属，避免重复
    const secondary = (formData.value.secondaryOrgs || []).filter((id: number) => id !== primaryOrg)
    const payload: Api.User.UserSaveParams = {
      id: editingId.value,
      name,
      employeeNo: formData.value.employeeNo || '',
      // 手机号已从表单下线：新增不传（后端选填），编辑原样回传避免覆盖存量值
      mobile: formData.value.mobile ?? undefined,
      email: formData.value.email,
      managerUserId: formData.value.manager ?? null,
      username,
      password: isEdit.value ? formData.value.password || undefined : password,
      orgIds: [primaryOrg, ...secondary],
      primaryOrgId: primaryOrg,
      roleIds: formData.value.role || [],
      status: formData.value.status ?? 1,
      // 仅新增且由组织管理页带入机构时下发身份；编辑时不下发（避免误改机构负责人）
      asOrgLeader:
        !isEdit.value && presetOrg.value ? formData.value.orgRole === 'leader' : null
    }
    if (isEdit.value) {
      await fetchUpdateUser(payload)
    } else {
      await fetchCreateUser(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    visible.value = false
    emit('success')
  }

  defineExpose({ open })
</script>
