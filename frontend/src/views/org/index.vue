<!-- 组织管理（2026-09-11 用户需求：部门管理与用户管理合并为本页）
     左侧机构树：机构的新增 / 编辑 / 删除 / 人员转移；右侧用户表格：所选机构下用户的新增 / 编辑 / 重置密码 / 启停 / 离职。
     新增用户必须先选中左侧机构，并选择「身份」——机构管理者（同时成为该机构负责人）或下级成员（直属主管默认为该机构负责人）。 -->
<template>
  <div class="page-content flex" style="height: var(--art-full-height)">
    <!-- ==================== 左：机构树 ==================== -->
    <div class="art-card-sm mt-2 mr-3 flex w-72 shrink-0 flex-col p-3">
      <div class="flex-cb mb-2 shrink-0">
        <span class="text-sm font-medium">{{ t('system.org.treeTitle') }}</span>
        <div class="flex items-center gap-1">
          <ElButton size="small" type="primary" v-ripple @click="openOrgForm()">
            <ArtSvgIcon icon="ri:add-line" />
          </ElButton>
          <ElButton size="small" :disabled="!currentOrg" v-ripple @click="openOrgForm(currentOrg)">
            <ArtSvgIcon icon="ri:edit-line" />
          </ElButton>
          <ElButton size="small" :disabled="!currentOrg" v-ripple @click="handleDeleteOrg">
            <ArtSvgIcon icon="ri:delete-bin-line" />
          </ElButton>
          <ElButton size="small" :disabled="!currentOrg" v-ripple @click="openTransfer">
            <ArtSvgIcon icon="ri:exchange-line" />
          </ElButton>
        </div>
      </div>
      <ElScrollbar class="flex-1 min-h-0">
        <ElTree
          ref="orgTreeRef"
          :data="orgTree"
          node-key="id"
          highlight-current
          default-expand-all
          :expand-on-click-node="false"
          :props="{ label: 'name', children: 'children' }"
          @node-click="handleOrgSelect"
        >
          <template #default="{ data }">
            <span class="flex items-center gap-1">
              <span class="truncate">{{ data.name }}</span>
              <span class="shrink-0 text-xs text-g-500">{{ data.userCount || 0 }}</span>
            </span>
          </template>
        </ElTree>
        <ElEmpty v-if="!orgTree.length" :description="t('system.org.selectFirstTip')" :image-size="60" />
      </ElScrollbar>
    </div>

    <!-- ==================== 右：用户表格 ==================== -->
    <div class="flex flex-1 min-w-0 flex-col">
      <ArtTableHeader
        :layout="'search,refresh,size,columns,settings'"
        class="mt-2 shrink-0"
        v-model:columns="columnChecks"
        :loading="loading"
        @refresh="refreshData"
      >
        <template #left>
          <div class="flex flex-wrap items-center">
            <ElButton type="primary" :disabled="!currentOrg" v-ripple @click="openUserForm()">
              <ArtSvgIcon icon="ri:add-line" class="mr-1" />
              {{ t('common.add') }}
            </ElButton>
            <ElButton v-ripple @click="handleReset">
              <ArtSvgIcon icon="ri:filter-off-line" class="mr-1" />
              {{ t('common.resetFilter') }}
            </ElButton>
            <span class="ml-3 text-xs text-g-500">
              {{ currentOrg ? `${t('system.org.currentOrg')}：${currentOrg.name}` : t('system.org.allUsers') }}
            </span>
          </div>
        </template>
      </ArtTableHeader>

      <div class="flex-1 min-h-0">
        <ArtTable
          :show-table-header="false"
          :loading="loading"
          :data="data"
          :columns="columns"
          row-key="id"
          :pagination="pagination"
          :pagination-options="{ pageSizes: [50, 100, 150], align: 'right' }"
          @pagination:size-change="handleSizeChange"
          @pagination:current-change="handleCurrentChange"
        >
          <template #name-header>
            <HeaderFilter
              :label="t('system.user.name')"
              v-model="searchForm.name"
              :placeholder="t('system.user.namePlaceholder')"
              @change="handleSearch"
            />
          </template>
          <template #employeeNo-header>
            <HeaderFilter
              :label="t('system.user.employeeNo')"
              v-model="searchForm.employeeNo"
              :placeholder="t('system.user.employeeNoPlaceholder')"
              @change="handleSearch"
            />
          </template>
          <template #mobile-header>
            <HeaderFilter
              :label="t('system.user.mobile')"
              v-model="searchForm.mobile"
              :placeholder="t('system.user.mobilePlaceholder')"
              @change="handleSearch"
            />
          </template>
          <template #username-header>
            <HeaderFilter
              :label="t('system.user.username')"
              v-model="searchForm.username"
              :placeholder="t('system.user.usernamePlaceholder')"
              @change="handleSearch"
            />
          </template>
          <template #status-header>
            <HeaderFilter
              :label="t('common.status')"
              v-model="searchForm.status"
              :options="[
                { label: t('common.enable'), value: '1' },
                { label: t('common.disable'), value: '0' }
              ]"
              @change="handleSearch"
            />
          </template>

          <!-- 身份：该用户是否为所属机构负责人 -->
          <template #orgLeader="{ row }">
            <ElTag v-if="row.orgLeader" size="small" type="success">{{ t('system.user.orgLeaderTag') }}</ElTag>
            <ElTag v-else size="small" type="info">{{ t('system.user.orgMemberTag') }}</ElTag>
          </template>

          <template #roleNames="{ row }">
            <span>{{ row.roleNames?.join('、') || '—' }}</span>
          </template>

          <template #managerName="{ row }">
            <span>{{ row.managerName || '—' }}</span>
          </template>

          <template #status="{ row }">
            <ElSwitch
              :model-value="row.status === 1"
              :loading="statusLoadingId === row.id"
              @change="(val) => handleStatusChange(row, val)"
            />
          </template>

          <template #operation="{ row }">
            <div class="flex items-center">
              <ArtButtonTable type="edit" @click="openUserForm(row)" />
              <ArtButtonTable type="view" icon="ri:key-2-line" @click="openResetPassword(row)" />
              <!-- 2026-09-11 用户需求：去掉「离职」按钮——与「状态」开关语义重叠，统一用状态启停
              <ArtButtonTable type="delete" icon="ri:logout-circle-r-line" @click="handleLeave(row)" />
              -->
              <ArtButtonTable type="delete" @click="handleDeleteUser(row)" />
            </div>
          </template>
        </ArtTable>
      </div>
    </div>

    <OrgDialog ref="orgDialogRef" @success="reloadTree" />
    <TransferDialog ref="transferDialogRef" @success="reloadTree" />
    <UserDialog ref="userDialogRef" @success="onUserSaved" />
    <ResetPasswordDialog ref="resetPasswordDialogRef" />
  </div>
</template>

<script setup lang="ts">
  import { fetchDeleteOrg, fetchOrgTree } from '@/api/org'
  import { fetchChangeUserStatus, fetchDeleteUser, fetchUserLeave, fetchUserPage } from '@/api/user'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { buildFilters } from '@/components/business/header-filter/filters'
  import type { ColumnOption } from '@/types'
  import OrgDialog from './modules/org-dialog.vue'
  import TransferDialog from './modules/transfer-dialog.vue'
  import UserDialog from '@/views/user/modules/user-dialog.vue'
  import ResetPasswordDialog from '@/views/user/modules/reset-password-dialog.vue'

  defineOptions({ name: 'OrgIndex' })

  const { t } = useI18n()

  // ==================== 机构树 ====================

  const orgTree = ref<Api.Org.OrgTreeNode[]>([])
  const currentOrg = ref<Api.Org.OrgTreeNode>()
  const orgTreeRef = useTemplateRef<{ setCurrentKey: (key: number | null) => void }>('orgTreeRef')

  const findOrg = (nodes: Api.Org.OrgTreeNode[], id: number): Api.Org.OrgTreeNode | undefined => {
    for (const node of nodes || []) {
      if (node.id === id) {
        return node
      }
      const found = findOrg(node.children || [], id)
      if (found) {
        return found
      }
    }
    return undefined
  }

  const reloadTree = async () => {
    orgTree.value = await fetchOrgTree()
    // 刷新后保持选中（机构可能已被改名或删除）
    if (currentOrg.value) {
      const kept = findOrg(orgTree.value, currentOrg.value.id)
      currentOrg.value = kept
      if (!kept) {
        orgTreeRef.value?.setCurrentKey(null)
        handleOrgSelect(undefined)
      }
    }
  }

  const handleOrgSelect = (node?: Api.Org.OrgTreeNode) => {
    currentOrg.value = node
    // 切换机构即换查询条件：回到第一页（未选中 = 全部用户）
    replaceSearchParams(node ? { orgId: node.id } : {})
    fetchData()
  }

  const orgDialogRef = useTemplateRef<{
    open: (row?: Api.Org.OrgTreeNode, presetParentId?: number) => void
  }>('orgDialogRef')
  const transferDialogRef = useTemplateRef<{ open: (row: Api.Org.OrgTreeNode) => void }>('transferDialogRef')

  const openOrgForm = (row?: Api.Org.OrgTreeNode) => {
    if (row) {
      orgDialogRef.value?.open(row)
    } else {
      // 新增：未选中机构时建顶级，选中时作为其下级
      orgDialogRef.value?.open(undefined, currentOrg.value?.id ?? 0)
    }
  }

  const openTransfer = () => {
    if (currentOrg.value) {
      transferDialogRef.value?.open(currentOrg.value)
    }
  }

  const handleDeleteOrg = async () => {
    if (!currentOrg.value) {
      return
    }
    await ElMessageBox.confirm(t('system.org.deleteTips'), t('common.tips'), { type: 'warning' })
    await fetchDeleteOrg({ id: currentOrg.value.id })
    ElMessage.success(t('common.deleteSuccess'))
    currentOrg.value = undefined
    orgTreeRef.value?.setCurrentKey(null)
    reloadTree()
  }

  // ==================== 用户表格 ====================

  const searchForm = ref<Record<string, any>>({})

  const columnsFactory = () =>
    [
      { prop: 'name', label: t('system.user.name'), minWidth: 100, useHeaderSlot: true },
      { prop: 'employeeNo', label: t('system.user.employeeNo'), minWidth: 90, useHeaderSlot: true },
      { prop: 'mobile', label: t('system.user.mobile'), minWidth: 110, useHeaderSlot: true },
      { prop: 'username', label: t('system.user.username'), minWidth: 100, useHeaderSlot: true },
      { prop: 'orgLeader', label: t('system.user.orgRole'), minWidth: 100, useSlot: true },
      { prop: 'roleNames', label: t('system.user.role'), minWidth: 110, useSlot: true },
      { prop: 'managerName', label: t('system.user.manager'), minWidth: 90, useSlot: true },
      { prop: 'status', label: t('common.status'), minWidth: 95, useSlot: true, useHeaderSlot: true },
      {
        prop: 'operation',
        label: t('common.operation'),
        // 3 个行内按钮（编辑/重置密码/删除），按 ArtButtonTable 约 44px 每个预留
        width: 165,
        fixed: 'right',
        useSlot: true
      }
    ] as ColumnOption<Api.User.UserListItem>[]

  const { columns, columnChecks } = useTableColumns(columnsFactory)

  const {
    data,
    loading,
    pagination,
    replaceSearchParams,
    handleSizeChange,
    handleCurrentChange,
    fetchData,
    refreshData,
    refreshUpdate
  } = useTable({
    core: { apiFn: fetchUserPage, apiParams: { size: 50 } }
  })

  const handleSearch = () => {
    const params: Record<string, any> = { ...searchForm.value }
    // 保留当前机构过滤（表头漏斗只叠加姓名/工号/手机号/账号/状态）
    if (currentOrg.value) {
      params.orgId = currentOrg.value.id
    }
    const filters = buildFilters(searchForm.value, {
      username: 'like',
      roleNames: 'like',
      managerName: 'like'
    })
    if (filters) {
      params.filters = filters
    }
    replaceSearchParams(params)
    fetchData()
  }

  /** 重置：清空姓名/工号等条件，机构过滤保留 */
  const handleReset = () => {
    searchForm.value = {}
    replaceSearchParams(currentOrg.value ? { orgId: currentOrg.value.id } : {})
    fetchData()
  }

  const statusLoadingId = ref<number | null>(null)

  /** 删除用户：物理删除（连账号与部门/角色绑定一并移除），二次确认 */
  const handleDeleteUser = async (row: Api.User.UserListItem) => {
    await ElMessageBox.confirm(t('system.user.deleteTips'), t('common.tips'), { type: 'warning' })
    await fetchDeleteUser(row.id)
    ElMessage.success(t('common.deleteSuccess'))
    refreshUpdate()
    reloadTree()
  }

  const handleStatusChange = async (row: Api.User.UserListItem, val: string | number | boolean) => {
    statusLoadingId.value = row.id
    try {
      await fetchChangeUserStatus({ userId: row.id, status: val ? 1 : 0 })
      ElMessage.success(t('common.operateSuccess'))
      refreshUpdate()
    } finally {
      statusLoadingId.value = null
    }
  }

  // 2026-09-11 用户需求：界面不再提供「离职」入口（与「状态」重叠），方法保留备查
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const handleLeave = async (row: Api.User.UserListItem) => {
    await ElMessageBox.confirm(t('system.user.leaveTips'), t('common.tips'), { type: 'warning' })
    await fetchUserLeave({ userId: row.id })
    ElMessage.success(t('system.user.leaveSuccess'))
    refreshUpdate()
    reloadTree()
  }

  // ==================== 用户弹窗 ====================

  const userDialogRef = useTemplateRef<{
    open: (row?: Api.User.UserListItem, org?: { id: number; name: string }) => void
  }>('userDialogRef')
  const resetPasswordDialogRef = useTemplateRef<{ open: (row: Api.User.UserListItem) => void }>('resetPasswordDialogRef')

  const openUserForm = (row?: Api.User.UserListItem) => {
    userDialogRef.value?.open(
      row,
      currentOrg.value ? { id: currentOrg.value.id, name: currentOrg.value.name } : undefined
    )
  }

  const openResetPassword = (row: Api.User.UserListItem) => {
    resetPasswordDialogRef.value?.open(row)
  }

  /** 用户保存后：刷新列表与机构树（管理者身份会写入机构负责人、人数也会变） */
  const onUserSaved = () => {
    refreshUpdate()
    reloadTree()
  }

  onMounted(() => {
    reloadTree()
  })
</script>
