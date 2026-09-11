<!-- 基础数据（枚举字典）管理：字典列表 + 字段管理 + 枚举项管理 -->
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
        <template #code-header>
          <HeaderFilter
            :label="t('system.dict.code')"
            v-model="searchForm.code"
            :placeholder="t('system.dict.code')"
            @change="handleSearch"
          />
        </template>

        <template #name-header>
          <HeaderFilter
            :label="t('system.dict.name')"
            v-model="searchForm.name"
            :placeholder="t('system.dict.name')"
            @change="handleSearch"
          />
        </template>

        <template #description-header>
          <HeaderFilter
            :label="t('system.dict.description')"
            v-model="searchForm.description"
            :placeholder="t('system.dict.description')"
            @change="handleSearch"
          />
        </template>

        <template #fieldCount-header>
          <HeaderFilter
            :label="t('system.dict.fieldCount')"
            v-model="searchForm.fieldCount"
            mode="number"
            :placeholder="t('system.dict.fieldCount')"
            @change="handleSearch"
          />
        </template>

        <template #itemCount-header>
          <HeaderFilter
            :label="t('system.dict.itemCount')"
            v-model="searchForm.itemCount"
            mode="number"
            :placeholder="t('system.dict.itemCount')"
            @change="handleSearch"
          />
        </template>

        <template #isBuiltin-header>
          <HeaderFilter
            :label="t('system.dict.builtin')"
            v-model="searchForm.isBuiltin"
            :options="[{ label: t('system.dict.builtin'), value: '1' }, { label: t('system.dict.custom'), value: '0' }]"
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

        <template #sort-header>
          <HeaderFilter
            :label="t('system.dict.sort')"
            v-model="searchForm.sort"
            mode="number"
            :placeholder="t('system.dict.sort')"
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
        <template #isBuiltin="{ row }">
          <ElTag :type="row.isBuiltin === 1 ? 'warning' : 'info'">
            {{ row.isBuiltin === 1 ? t('system.dict.builtin') : t('system.dict.custom') }}
          </ElTag>
        </template>

        <template #status="{ row }">
          <ElTag :type="row.status === 1 ? 'success' : 'info'">
            {{ row.status === 1 ? t('common.enabled') : t('common.disabled') }}
          </ElTag>
        </template>

        <template #operation="{ row }">
          <div class="flex items-center">
            <ArtButtonTable type="view" icon="ri:list-settings-line" @click="openFields(row)" />
            <ArtButtonTable type="view" icon="ri:list-check-2" @click="openItems(row)" />
            <ArtButtonTable type="edit" @click="openForm(row)" />
            <ArtButtonTable type="delete" @click="handleDelete(row)" />
          </div>
        </template>
      </ArtTable>

    </div>

    <DictDialog ref="dictDialogRef" @success="refreshUpdate" />
    <FieldDrawer ref="fieldDrawerRef" />
    <ItemDrawer ref="itemDrawerRef" />
  </div>
</template>

<script setup lang="ts">
  import { fetchDeleteDict, fetchDictPage } from '@/api/dict'
  import { ElMessageBox } from 'element-plus'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { buildFilters } from '@/components/business/header-filter/filters'
  import type { ColumnOption } from '@/types'
  import DictDialog from './modules/dict-dialog.vue'
  import FieldDrawer from './modules/field-drawer.vue'
  import ItemDrawer from './modules/item-drawer.vue'

  defineOptions({ name: 'DictIndex' })

  const { t } = useI18n()

  /** 搜索表单（与 useTable 的 searchParams 解耦，避免分页字段被清空） */
  const searchForm = ref<Record<string, any>>({})

  const searchItems = computed(() => [
    {
      key: 'name',
      label: t('system.dict.name'),
      type: 'input',
      props: { placeholder: t('system.dict.namePlaceholder'), clearable: true }
    },
    {
      key: 'code',
      label: t('system.dict.code'),
      type: 'input',
      props: { placeholder: t('system.dict.codePlaceholder'), clearable: true }
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

  const columnsFactory = () =>
    [
      { type: 'index', label: t('common.index'), width: 60 },
      { prop: 'code', label: t('system.dict.code'), minWidth: 120, useHeaderSlot: true },
      { prop: 'name', label: t('system.dict.name'), minWidth: 110, useHeaderSlot: true },
      { prop: 'description', label: t('system.dict.description'), minWidth: 120, useHeaderSlot: true },
      { prop: 'fieldCount', label: t('system.dict.fieldCount'), minWidth: 90, useHeaderSlot: true },
      { prop: 'itemCount', label: t('system.dict.itemCount'), minWidth: 95, useHeaderSlot: true },
      { prop: 'isBuiltin', label: t('system.role.type'), minWidth: 100, useSlot: true, useHeaderSlot: true },
      { prop: 'status', label: t('common.status'), minWidth: 95, useSlot: true, useHeaderSlot: true },
      { prop: 'sort', label: t('system.dict.sort'), minWidth: 85, useHeaderSlot: true },
      { prop: 'updateTime', label: t('system.user.createTime'), minWidth: 140, useHeaderSlot: true },
      {
        prop: 'operation',
        label: t('common.operation'),
        width: 175,
        fixed: 'right',
        useSlot: true
      }
    ] as ColumnOption<Api.Dict.DictListItem>[]

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
    core: { apiFn: fetchDictPage, apiParams: { size: 50 } }
  })

  /** 查询：替换搜索条件并回到第一页 */
  const handleSearch = () => {
    const params: Record<string, any> = { ...searchForm.value }
    // 2026-09-10 用户需求：新增漏斗列走通用 filters 参数
    const filters = buildFilters(searchForm.value, {
      description: 'like',
      fieldCount: 'gte',
      itemCount: 'gte',
      isBuiltin: 'in',
      sort: 'gte',
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

  /** 删除：内置不可删；存在启用枚举项时后端拦截 */
  const handleDelete = async (row: Api.Dict.DictListItem) => {
    if (row.isBuiltin === 1) {
      ElMessage.warning(t('system.dict.builtinDeleteTips'))
      return
    }
    await ElMessageBox.confirm(t('common.deleteTips'), t('common.tips'), { type: 'warning' })
    await fetchDeleteDict({ id: row.id })
    ElMessage.success(t('common.deleteSuccess'))
    refreshRemove()
  }

  const dictDialogRef = useTemplateRef<{
    open: (row?: Api.Dict.DictListItem) => void
  }>('dictDialogRef')
  const fieldDrawerRef = useTemplateRef<{
    open: (row: Api.Dict.DictListItem) => void
  }>('fieldDrawerRef')
  const itemDrawerRef = useTemplateRef<{
    open: (row: Api.Dict.DictListItem) => void
  }>('itemDrawerRef')

  const openForm = (row?: Api.Dict.DictListItem) => {
    dictDialogRef.value?.open(row)
  }

  const openFields = (row: Api.Dict.DictListItem) => {
    fieldDrawerRef.value?.open(row)
  }

  const openItems = (row: Api.Dict.DictListItem) => {
    itemDrawerRef.value?.open(row)
  }
</script>
