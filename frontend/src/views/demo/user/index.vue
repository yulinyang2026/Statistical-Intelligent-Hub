<!-- 演示：用户管理（简单查询 + 高级查询 + 表格） -->
<template>
  <div class="page-content">
    <!-- 简单查询：常用字段 -->
    <ArtSearchBar
      v-model="searchForm"
      :items="searchItems"
      :show-expand="false"
      @search="handleSearch"
      @reset="handleReset"
    />

    <!-- 表格工具栏：高级查询入口 + 刷新 / 列设置 / 全屏 -->
    <ArtTableHeader
      class="mt-4"
      v-model:columns="columnChecks"
      :loading="loading"
      @refresh="refreshData"
    >
      <template #left>
        <ElButton v-ripple @click="openAdvancedSearch">
          <ArtSvgIcon icon="ri:filter-3-line" class="mr-1" />
          {{ t('demo.user.advancedSearch') }}
        </ElButton>
        <ElTag v-if="activeAdvancedCount > 0" class="ml-2" type="primary" effect="light">
          {{ t('demo.user.advancedFilterCount', { count: activeAdvancedCount }) }}
        </ElTag>
      </template>
    </ArtTableHeader>

    <ArtTable
      :loading="loading"
      :data="data"
      :columns="columns"
      :pagination="pagination"
      @pagination:size-change="handleSizeChange"
      @pagination:current-change="handleCurrentChange"
    >
      <template #gender="{ row }">
        <ElTag :type="row.gender === '1' ? 'primary' : 'danger'" effect="plain">
          {{ row.gender === '1' ? t('demo.user.male') : t('demo.user.female') }}
        </ElTag>
      </template>

      <template #status="{ row }">
        <ElTag :type="row.status === '1' ? 'success' : 'info'">
          {{ row.status === '1' ? t('demo.user.enabled') : t('demo.user.disabled') }}
        </ElTag>
      </template>

      <template #operation="{ row }">
        <div class="flex items-center">
          <ArtButtonTable type="view" @click="handleView(row)" />
          <ArtButtonTable type="edit" @click="handleEdit(row)" />
          <ArtButtonTable type="delete" @click="handleDelete(row)" />
        </div>
      </template>
    </ArtTable>

    <!-- 高级查询抽屉：多字段组合查询 -->
    <AdvancedSearchDrawer ref="advancedDrawerRef" @search="handleAdvancedSearch" />
  </div>
</template>

<script setup lang="ts">
  import { deleteDemoUser, fetchDemoUserPage } from '@/api/demo'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable } from '@/hooks'
  import type { ColumnOption } from '@/types'
  import AdvancedSearchDrawer from './modules/advanced-search-drawer.vue'

  defineOptions({ name: 'DemoUser' })

  const { t } = useI18n()

  /** 简单查询表单（与 useTable 的 searchParams 解耦，避免分页字段被清空） */
  const searchForm = ref<Record<string, any>>({})

  /** 简单查询字段 */
  const searchItems = computed(() => [
    {
      key: 'userName',
      label: t('demo.user.userName'),
      type: 'input',
      props: { placeholder: t('demo.user.userNamePlaceholder'), clearable: true, maxlength: 50 }
    },
    {
      key: 'nickName',
      label: t('demo.user.nickName'),
      type: 'input',
      props: { placeholder: t('demo.user.nickNamePlaceholder'), clearable: true, maxlength: 50 }
    },
    {
      key: 'status',
      label: t('demo.user.status'),
      type: 'select',
      props: {
        placeholder: t('demo.user.statusPlaceholder'),
        clearable: true,
        options: [
          { label: t('demo.user.enabled'), value: '1' },
          { label: t('demo.user.disabled'), value: '2' }
        ]
      }
    }
  ])

  /** 表格列配置 */
  const columnsFactory = () =>
    [
      { type: 'globalIndex', label: t('common.index'), width: 60 },
      { prop: 'userName', label: t('demo.user.userName'), minWidth: 120 },
      { prop: 'nickName', label: t('demo.user.nickName'), minWidth: 120 },
      { prop: 'gender', label: t('demo.user.gender'), width: 80, useSlot: true },
      { prop: 'dept', label: t('demo.user.dept'), minWidth: 110 },
      { prop: 'role', label: t('demo.user.role'), minWidth: 100 },
      { prop: 'age', label: t('demo.user.age'), width: 70 },
      { prop: 'phone', label: t('demo.user.phone'), minWidth: 130 },
      { prop: 'email', label: t('demo.user.email'), minWidth: 190 },
      { prop: 'status', label: t('demo.user.status'), width: 90, useSlot: true },
      { prop: 'createTime', label: t('demo.user.createTime'), minWidth: 170 },
      {
        prop: 'operation',
        label: t('common.operation'),
        width: 150,
        fixed: 'right',
        useSlot: true
      }
    ] as ColumnOption<Api.Demo.UserListItem>[]

  const {
    data,
    loading,
    pagination,
    columns,
    columnChecks,
    replaceSearchParams,
    resetSearchParams,
    handleSizeChange,
    handleCurrentChange,
    fetchData,
    refreshData,
    refreshRemove
  } = useTable({
    core: {
      apiFn: fetchDemoUserPage,
      columnsFactory
    }
  })

  /** 已生效的高级查询条件（接口参数形态） */
  const advancedParams = ref<Record<string, any>>({})

  /** 高级查询生效字段数（用于提示标签） */
  const activeAdvancedCount = computed(
    () => Object.values(advancedParams.value).filter((value) => value !== undefined && value !== '')
      .length
  )

  const advancedDrawerRef = useTemplateRef<{ open: (initial?: Record<string, any>) => void }>(
    'advancedDrawerRef'
  )

  /** 打开高级查询抽屉（回填已生效条件） */
  const openAdvancedSearch = () => {
    advancedDrawerRef.value?.open(advancedParams.value)
  }

  /** 简单查询：替换搜索条件并回到第一页（高级条件同时失效） */
  const handleSearch = () => {
    advancedParams.value = {}
    replaceSearchParams({ ...searchForm.value })
    fetchData()
  }

  /** 简单查询重置：清空全部条件（useTable 内部会自动重新请求） */
  const handleReset = () => {
    searchForm.value = {}
    advancedParams.value = {}
    resetSearchParams()
  }

  /** 高级查询提交：条件替换并回到第一页 */
  const handleAdvancedSearch = (params: Record<string, any>) => {
    advancedParams.value = { ...params }
    searchForm.value = {}
    replaceSearchParams({ ...params })
    fetchData()
  }

  /** 查看演示 */
  const handleView = (row: Api.Demo.UserListItem) => {
    ElMessage.info(`${t('demo.user.viewTip')}：${row.userName} - ${row.nickName}`)
  }

  /** 编辑演示 */
  const handleEdit = (row: Api.Demo.UserListItem) => {
    ElMessage.info(`${t('demo.user.editTip')}：${row.userName}`)
  }

  /** 删除（Mock 数据真实删除） */
  const handleDelete = async (row: Api.Demo.UserListItem) => {
    await ElMessageBox.confirm(t('demo.user.deleteTips'), t('common.tips'), { type: 'warning' })
    await deleteDemoUser({ id: row.id })
    ElMessage.success(t('demo.user.deleteSuccess'))
    refreshRemove()
  }
</script>
