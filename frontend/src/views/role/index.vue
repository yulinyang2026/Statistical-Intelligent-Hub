<!-- 角色管理：角色 CRUD + 授权（菜单/接口/数据权限 + 数据范围） -->
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
            :label="t('system.role.name')"
            v-model="searchForm.name"
            :placeholder="t('system.role.namePlaceholder')"
            @change="handleSearch"
          />
        </template>

        <template #code-header>
          <HeaderFilter
            :label="t('system.role.code')"
            v-model="searchForm.code"
            :placeholder="t('system.role.codePlaceholder')"
            @change="handleSearch"
          />
        </template>

        <template #type-header>
          <HeaderFilter
            :label="t('system.role.type')"
            v-model="searchForm.type"
            :options="[{ label: t('system.role.builtin'), value: '1' }, { label: t('system.role.custom'), value: '2' }]"
            @change="handleSearch"
          />
        </template>

        <template #dataScope-header>
          <HeaderFilter
            :label="t('system.role.dataScope')"
            v-model="searchForm.dataScope"
            :options="dataScopeFilterOptions"
            @change="handleSearch"
          />
        </template>

        <template #userCount-header>
          <HeaderFilter
            :label="t('system.role.userCount')"
            v-model="searchForm.userCount"
            mode="number"
            :placeholder="t('system.role.userCount')"
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

        <template #updateTime-header>
          <HeaderFilter
            :label="t('system.user.createTime')"
            v-model="searchForm.updateTime"
            mode="daterange"
            @change="handleSearch"
          />
        </template>
        <template #type="{ row }">
          <ElTag :type="row.type === 1 ? 'warning' : 'info'">
            {{ row.type === 1 ? t('system.role.builtin') : t('system.role.custom') }}
          </ElTag>
        </template>

        <template #dataScope="{ row }">
          <span>{{ scopeLabel(row.dataScope) }}</span>
        </template>

        <template #status="{ row }">
          <ElTag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
          </ElTag>
        </template>

        <template #operation="{ row }">
          <div class="flex items-center">
            <ArtButtonTable type="edit" @click="openForm(row)" />
            <ArtButtonTable type="view" icon="ri:shield-check-line" @click="openAssign(row)" />
            <ArtButtonTable type="delete" @click="handleDelete(row)" />
          </div>
        </template>
      </ArtTable>

    </div>

    <RoleDialog ref="roleDialogRef" @success="refreshUpdate" />
    <AssignDialog ref="assignDialogRef" @success="refreshUpdate" />
  </div>
</template>

<script setup lang="ts">
  import { fetchDeleteRole, fetchRolePage } from '@/api/role'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { buildFilters } from '@/components/business/header-filter/filters'
  import type { ColumnOption } from '@/types'
  import RoleDialog from './modules/role-dialog.vue'
  import AssignDialog from './modules/assign-dialog.vue'

  defineOptions({ name: 'RoleIndex' })

  const { t } = useI18n()

  /** 搜索表单（与 useTable 的 searchParams 解耦，避免分页字段被清空） */
  const searchForm = ref<Record<string, any>>({})

  /** 数据范围选项（列表展示与弹窗共用口径） */
  const dataScopeOptions = computed(() => [
    { label: t('system.role.scopeAll'), value: 1 },
    { label: t('system.role.scopeOrgAndChildren'), value: 2 },
    { label: t('system.role.scopeOrgOnly'), value: 3 },
    { label: t('system.role.scopeSelf'), value: 4 },
    { label: t('system.role.scopeSelfAndSubordinates'), value: 5 },
    { label: t('system.role.scopeCustom'), value: 6 }
  ])

  const scopeLabel = (dataScope: number) =>
    dataScopeOptions.value.find((option) => option.value === dataScope)?.label || '-'

  const searchItems = computed(() => [
    {
      key: 'name',
      label: t('system.role.name'),
      type: 'input',
      props: { placeholder: t('system.role.namePlaceholder'), clearable: true }
    },
    {
      key: 'code',
      label: t('system.role.code'),
      type: 'input',
      props: { placeholder: t('system.role.codePlaceholder'), clearable: true }
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
    }
  ])

  /** 数据范围漏斗候选（与角色表单一致：6 档） */
  const dataScopeFilterOptions = computed(() => [
    { label: t('system.role.scopeAll'), value: '1' },
    { label: t('system.role.scopeOrgAndChildren'), value: '2' },
    { label: t('system.role.scopeOrgOnly'), value: '3' },
    { label: t('system.role.scopeSelf'), value: '4' },
    { label: t('system.role.scopeSelfAndSubordinates'), value: '5' },
    { label: t('system.role.scopeCustom'), value: '6' }
  ])

  const columnsFactory = () =>
    [
      // 2026-09-11 用户反馈：列间疏密不均——原先「类型 / 人数 / 状态」是固定 width，富余宽度被少数
      // 弹性列全部吃掉，导致有的列贴太近、有的拉太开。改为**除序号与操作列外全部走 minWidth 弹性**，
      // 富余宽度按比例分摊到每一列，与模块/专题/项目列表口径一致（全局 FR-UI-009）
      { type: 'index', label: t('common.index'), width: 60 },
      { prop: 'name', label: t('system.role.name'), minWidth: 140, useHeaderSlot: true },
      { prop: 'code', label: t('system.role.code'), minWidth: 130, useHeaderSlot: true },
      { prop: 'type', label: t('system.role.type'), minWidth: 100, useSlot: true, useHeaderSlot: true },
      { prop: 'dataScope', label: t('system.role.dataScope'), minWidth: 120, useSlot: true, useHeaderSlot: true },
      { prop: 'userCount', label: t('system.role.userCount'), minWidth: 90, useHeaderSlot: true },
      { prop: 'status', label: t('common.status'), minWidth: 95, useSlot: true, useHeaderSlot: true },
      { prop: 'updateTime', label: t('system.user.createTime'), minWidth: 160, useHeaderSlot: true },
      {
        prop: 'operation',
        label: t('common.operation'),
        width: 165,
        fixed: 'right',
        useSlot: true
      }
    ] as ColumnOption<Api.Role.RoleListItem>[]

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
    refreshUpdate,
    refreshRemove
  } = useTable({
    core: { apiFn: fetchRolePage, apiParams: { size: 50 } }
  })

  /** 查询：替换搜索条件并回到第一页 */
  const handleSearch = () => {
    const params: Record<string, any> = { ...searchForm.value }
    // 2026-09-10 用户需求：新增漏斗列走通用 filters 参数
    const filters = buildFilters(searchForm.value, {
      type: 'in',
      dataScope: 'in',
      userCount: 'gte',
      updateTime: 'between'
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

  /** 删除：内置角色不可删 */
  const handleDelete = async (row: Api.Role.RoleListItem) => {
    if (row.type === 1) {
      ElMessage.warning(t('system.role.builtinRoleDeleteTips'))
      return
    }
    await ElMessageBox.confirm(t('common.deleteTips'), t('common.tips'), { type: 'warning' })
    await fetchDeleteRole({ id: row.id })
    ElMessage.success(t('common.deleteSuccess'))
    refreshRemove()
  }

  const roleDialogRef = useTemplateRef<{
    open: (row?: Api.Role.RoleListItem) => void
  }>('roleDialogRef')
  const assignDialogRef = useTemplateRef<{
    open: (row: Api.Role.RoleListItem) => void
  }>('assignDialogRef')

  const openForm = (row?: Api.Role.RoleListItem) => {
    roleDialogRef.value?.open(row)
  }

  const openAssign = (row: Api.Role.RoleListItem) => {
    assignDialogRef.value?.open(row)
  }
</script>
