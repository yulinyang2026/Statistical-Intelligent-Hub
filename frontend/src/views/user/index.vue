<!-- 用户管理：人员档案 + 账号 + 部门归属 + 角色分配（新增/编辑/重置密码/启停/离职） -->
<template>
  <div class="page-content flex flex-col" style="height: var(--art-full-height)">
    <!-- 2026-09-10 按需求注释：查询控件改为表头漏斗筛选（每一列均带漏斗），原查询组件代码保留备查
    <ArtSearchBar
      v-model="searchForm"
      :items="searchItems"
      :show-expand="false"
      @search="handleSearch"
      @reset="handleReset"
    />
    -->

    <ArtTableHeader
      :layout="'search,refresh,size,columns,settings'"
      class="mt-2 shrink-0"
      v-model:columns="columnChecks"
      :loading="loading"
      @refresh="refreshData"
    >
      <template #left>
        <ElButton type="primary" v-ripple @click="openForm()">
          <ArtSvgIcon icon="ri:add-line" class="mr-1" />
          {{ t('common.add') }}
        </ElButton>
        <!-- 2026-09-10 查询条件移到表头后，保留统一重置入口 -->
        <ElButton v-ripple @click="handleReset">
          <ArtSvgIcon icon="ri:filter-off-line" class="mr-1" />
          {{ t('common.resetFilter') }}
        </ElButton>
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
        <!-- 表头筛选（2026-09-10 用户需求：所有字段均带漏斗） -->
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
            :placeholder="t('system.user.employeeNo')"
            @change="handleSearch"
          />
        </template>

        <template #mobile-header>
          <HeaderFilter
            :label="t('system.user.mobile')"
            v-model="searchForm.mobile"
            :placeholder="t('system.user.mobile')"
            @change="handleSearch"
          />
        </template>

        <template #username-header>
          <HeaderFilter
            :label="t('system.user.username')"
            v-model="searchForm.username"
            :placeholder="t('system.user.username')"
            @change="handleSearch"
          />
        </template>

        <template #orgs-header>
          <HeaderFilter
            :label="t('system.user.dept')"
            v-model="searchForm.orgs"
            :placeholder="t('system.user.deptPlaceholder')"
            @change="handleSearch"
          />
        </template>

        <template #roleNames-header>
          <HeaderFilter
            :label="t('system.user.role')"
            v-model="searchForm.roleNames"
            :placeholder="t('system.user.rolePlaceholder')"
            @change="handleSearch"
          />
        </template>

        <template #managerName-header>
          <HeaderFilter
            :label="t('system.user.manager')"
            v-model="searchForm.managerName"
            :placeholder="t('system.user.managerPlaceholder')"
            @change="handleSearch"
          />
        </template>

        <template #status-header>
          <HeaderFilter
            :label="t('common.status')"
            v-model="searchForm.status"
            :options="[{ label: t('common.enable'), value: '1' }, { label: t('common.disable'), value: '0' }]"
            @change="handleSearch"
          />
        </template>

        <template #createTime-header>
          <HeaderFilter
            :label="t('system.user.createTime')"
            v-model="searchForm.createTime"
            mode="daterange"
            @change="handleSearch"
          />
        </template>
        <template #orgs="{ row }">
          <template v-if="row.orgs?.length">
            <ElTag
              v-for="org in row.orgs"
              :key="org.orgId"
              :type="org.isPrimary ? 'success' : 'info'"
              class="mr-1"
            >
              {{ org.orgName }}
            </ElTag>
          </template>
          <span v-else>{{ t('system.user.noOrg') }}</span>
        </template>

        <template #roleNames="{ row }">
          <span>{{ row.roleNames?.join('、') || '-' }}</span>
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
            <ArtButtonTable type="edit" @click="openForm(row)" />
            <ArtButtonTable type="view" icon="ri:key-2-line" @click="openResetPassword(row)" />
            <ArtButtonTable type="delete" icon="ri:logout-circle-r-line" @click="handleLeave(row)" />
          </div>
        </template>
      </ArtTable>

    </div>

    <UserDialog ref="userDialogRef" @success="refreshUpdate" />
    <ResetPasswordDialog ref="resetPasswordDialogRef" />
  </div>
</template>

<script setup lang="ts">
  import { fetchChangeUserStatus, fetchUserLeave, fetchUserPage } from '@/api/user'
  import { fetchOrgTree } from '@/api/org'
  import { fetchRoleList } from '@/api/role'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { buildFilters } from '@/components/business/header-filter/filters'
  import type { ColumnOption } from '@/types'
  import UserDialog from './modules/user-dialog.vue'
  import ResetPasswordDialog from './modules/reset-password-dialog.vue'

  defineOptions({ name: 'UserIndex' })

  const { t } = useI18n()

  /** 搜索表单（与 useTable 的 searchParams 解耦，避免分页字段被清空） */
  const searchForm = ref<Record<string, any>>({})

  /** 部门树（搜索条件共用） */
  const orgTree = ref<Api.Org.OrgTreeNode[]>([])
  /** 角色下拉选项 */
  const roleOptions = ref<Api.Role.RoleOption[]>([])

  const searchItems = computed(() => [
    {
      key: 'name',
      label: t('system.user.name'),
      type: 'input',
      props: { placeholder: t('system.user.namePlaceholder'), clearable: true }
    },
    {
      key: 'employeeNo',
      label: t('system.user.employeeNo'),
      type: 'input',
      props: { placeholder: t('system.user.employeeNoPlaceholder'), clearable: true }
    },
    {
      key: 'mobile',
      label: t('system.user.mobile'),
      type: 'input',
      props: { placeholder: t('system.user.mobilePlaceholder'), clearable: true }
    },
    {
      key: 'status',
      label: t('common.status'),
      type: 'select',
      props: {
        placeholder: t('common.statusPlaceholder'),
        clearable: true,
        options: [
          { label: t('common.enable'), value: 1 },
          { label: t('common.disable'), value: 0 }
        ]
      }
    },
    {
      key: 'orgId',
      label: t('system.user.dept'),
      type: 'treeselect',
      props: {
        data: orgTree.value,
        nodeKey: 'id',
        props: { label: 'name', children: 'children' },
        placeholder: t('system.user.deptPlaceholder'),
        clearable: true,
        checkStrictly: true
      }
    },
    {
      key: 'roleId',
      label: t('system.user.role'),
      type: 'select',
      props: {
        placeholder: t('system.user.rolePlaceholder'),
        clearable: true,
        options: roleOptions.value.map((role) => ({ label: role.name, value: role.id }))
      }
    }
  ])

  const columnsFactory = () =>
    [
      { type: 'index', label: t('common.index'), width: 60 },
      { prop: 'name', label: t('system.user.name'), minWidth: 95, useHeaderSlot: true },
      { prop: 'employeeNo', label: t('system.user.employeeNo'), minWidth: 80, useHeaderSlot: true },
      { prop: 'mobile', label: t('system.user.mobile'), minWidth: 100, useHeaderSlot: true },
      { prop: 'username', label: t('system.user.username'), minWidth: 90, useHeaderSlot: true },
      { prop: 'orgs', label: t('system.user.dept'), minWidth: 120, useSlot: true, useHeaderSlot: true },
      { prop: 'roleNames', label: t('system.user.role'), minWidth: 95, useSlot: true, useHeaderSlot: true },
      { prop: 'managerName', label: t('system.user.manager'), minWidth: 80, useHeaderSlot: true },
      { prop: 'status', label: t('common.status'), width: 80, useSlot: true, useHeaderSlot: true },
      { prop: 'createTime', label: t('system.user.createTime'), minWidth: 130, useHeaderSlot: true },
      {
        prop: 'operation',
        label: t('common.operation'),
        width: 160,
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
    resetSearchParams,
    handleSizeChange,
    handleCurrentChange,
    fetchData,
    refreshData,
    refreshUpdate
  } = useTable({
    core: { apiFn: fetchUserPage, apiParams: { size: 50 } }
  })

  /** 查询：替换搜索条件并回到第一页 */
  const handleSearch = () => {
    const params: Record<string, any> = { ...searchForm.value }
    // 2026-09-10 用户需求：新增漏斗列走通用 filters 参数
    const filters = buildFilters(searchForm.value, {
      username: 'like',
      orgs: 'like',
      roleNames: 'like',
      managerName: 'like',
      createTime: 'between'
    })
    if (filters) {
      params.filters = filters
    } else {
      delete params.filters
    }
    replaceSearchParams(params)
    fetchData()
  }

  /** 重置：清空条件（useTable 内部会自动重新请求） */
  const handleReset = () => {
    searchForm.value = {}
    resetSearchParams()
  }

  /** 启停切换行级 loading 标记 */
  const statusLoadingId = ref<number | null>(null)

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

  /** 离职处理：禁用并解绑主属部门，保留历史 */
  const handleLeave = async (row: Api.User.UserListItem) => {
    await ElMessageBox.confirm(t('system.user.leaveTips'), t('common.tips'), { type: 'warning' })
    await fetchUserLeave({ userId: row.id })
    ElMessage.success(t('system.user.leaveSuccess'))
    refreshUpdate()
  }

  const userDialogRef = useTemplateRef<{
    open: (row?: Api.User.UserListItem) => void
  }>('userDialogRef')
  const resetPasswordDialogRef = useTemplateRef<{
    open: (row: Api.User.UserListItem) => void
  }>('resetPasswordDialogRef')

  const openForm = (row?: Api.User.UserListItem) => {
    userDialogRef.value?.open(row)
  }

  const openResetPassword = (row: Api.User.UserListItem) => {
    resetPasswordDialogRef.value?.open(row)
  }

  onMounted(async () => {
    const [tree, roles] = await Promise.all([fetchOrgTree(), fetchRoleList()])
    orgTree.value = tree
    roleOptions.value = roles
  })
</script>
