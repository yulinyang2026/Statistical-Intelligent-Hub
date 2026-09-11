<!-- 操作日志（2026-09-11 用户需求）：系统级全量操作轨迹，仅系统管理员可见（后端按 log:view 判权） -->
<template>
  <div class="page-content flex flex-col" style="height: var(--art-full-height)">
    <!-- 只读提示条：非管理员（无 log:view）时提示 -->
    <ElAlert
      v-if="!canView"
      type="warning"
      :closable="false"
      show-icon
      :title="t('system.log.readOnlyTip')"
      class="mb-4 shrink-0"
    />

    <ArtTableHeader
      :layout="'search,refresh,size,columns,settings'"
      class="mt-2 shrink-0"
      v-model:columns="columnChecks"
      :loading="loading"
      @refresh="refreshData"
    >
      <template #left>
        <div class="flex flex-wrap items-center">
          <ElButton v-ripple @click="handleReset">
            <ArtSvgIcon icon="ri:filter-off-line" class="mr-1" />
            {{ t('common.resetFilter') }}
          </ElButton>
          <span class="ml-3 text-xs text-g-500">{{ t('system.log.tip') }}</span>
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
        <!-- 表头漏斗（所有字段可筛：文本模糊 / 模块候选 / 时间区间） -->
        <template #time-header>
          <HeaderFilter
            :label="t('system.log.time')"
            v-model="searchForm.time"
            mode="daterange"
            @change="handleSearch"
          />
        </template>

        <template #operator-header>
          <HeaderFilter
            :label="t('system.log.operator')"
            v-model="searchForm.operator"
            :placeholder="t('system.log.operator')"
            @change="handleSearch"
          />
        </template>

        <template #module-header>
          <HeaderFilter
            :label="t('system.log.module')"
            v-model="searchForm.module"
            :options="moduleOptions"
            @change="handleSearch"
          />
        </template>

        <template #target-header>
          <HeaderFilter
            :label="t('system.log.target')"
            v-model="searchForm.target"
            :placeholder="t('system.log.target')"
            @change="handleSearch"
          />
        </template>

        <template #action-header>
          <HeaderFilter
            :label="t('system.log.action')"
            v-model="searchForm.action"
            :placeholder="t('system.log.action')"
            @change="handleSearch"
          />
        </template>

        <template #detail-header>
          <HeaderFilter
            :label="t('system.log.detail')"
            v-model="searchForm.detail"
            :placeholder="t('system.log.detail')"
            @change="handleSearch"
          />
        </template>

        <!-- 时间 -->
        <template #time="{ row }">
          <span class="text-g-600">{{ row.time || '—' }}</span>
        </template>

        <!-- 模块：显示中文名 -->
        <template #module="{ row }">
          <ElTag size="small" type="info">{{ moduleLabel(row.module) }}</ElTag>
        </template>

        <!-- 动作 -->
        <template #action="{ row }">
          <span class="font-medium">{{ row.action || '—' }}</span>
        </template>
      </ArtTable>
    </div>
  </div>
</template>

<script setup lang="ts">
  import { fetchLogPage } from '@/api/system-manage'
  import { fetchGetUserInfo } from '@/api/auth'
  import { useI18n } from 'vue-i18n'
  import { useTable, useTableColumns } from '@/hooks'
  import { useUserStore } from '@/store/modules/user'
  import type { ColumnOption } from '@/types'
  import HeaderFilter from '@/components/business/header-filter/index.vue'
  import { buildFilters } from '@/components/business/header-filter/filters'

  defineOptions({ name: 'LogIndex' })

  const { t } = useI18n()
  const userStore = useUserStore()

  /** 权限点判定（与其它页同款兜底：admin 全量放行） */
  const buttons = computed(() => userStore.info.buttons ?? [])
  const isSuper = computed(() => (userStore.info.roles ?? []).includes('R_SUPER'))
  const hasLogCodes = computed(() => buttons.value.some((code) => code.startsWith('log:')))
  const canView = computed(() => isSuper.value || !hasLogCodes.value || buttons.value.includes('log:view'))

  /** 搜索表单（与 useTable 解耦） */
  const searchForm = ref<Record<string, any>>({})

  /** 模块候选（值为后端模块标识，展示为中文名）
      2026-09-11 需求：登录/退出、组织、用户、角色、基础数据的写操作已全部补上日志，此处同步补齐筛选项 */
  const moduleOptions = computed(() => [
    { label: t('system.log.moduleAuth'), value: 'auth' },
    { label: t('menus.task.title'), value: 'task' },
    { label: t('menus.module.title'), value: 'module' },
    { label: t('system.log.moduleTopic'), value: 'topic' },
    { label: t('menus.project.title'), value: 'project' },
    { label: t('menus.doc.title'), value: 'doc' },
    { label: t('menus.org.index'), value: 'org' },
    { label: t('menus.user.index'), value: 'user' },
    { label: t('menus.role.index'), value: 'role' },
    { label: t('menus.dict.index'), value: 'dict' },
    { label: t('system.log.moduleMenu'), value: 'menu' }
  ])

  const moduleLabel = (value: string) =>
    moduleOptions.value.find((item) => item.value === value)?.label ?? value ?? '—'

  const columnsFactory = () =>
    [
      { prop: 'time', label: t('system.log.time'), minWidth: 170, useSlot: true, useHeaderSlot: true },
      { prop: 'operator', label: t('system.log.operator'), minWidth: 110, useHeaderSlot: true },
      { prop: 'module', label: t('system.log.module'), minWidth: 110, useSlot: true, useHeaderSlot: true },
      { prop: 'target', label: t('system.log.target'), minWidth: 180, useHeaderSlot: true },
      { prop: 'action', label: t('system.log.action'), minWidth: 130, useSlot: true, useHeaderSlot: true },
      { prop: 'detail', label: t('system.log.detail'), minWidth: 240, useHeaderSlot: true }
    ] as ColumnOption<Api.Log.LogItem>[]

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
    refreshData
  } = useTable({
    core: { apiFn: fetchLogPage, apiParams: { size: 50 } }
  })

  /** 查询：表头漏斗变更即触发（服务端查询 + 回第 1 页） */
  const handleSearch = () => {
    const params: Record<string, any> = {}
    if (searchForm.value.operator) params.operator = searchForm.value.operator
    // 时间区间：起止日期（含边界，后端按天比较）
    const range = Array.isArray(searchForm.value.time) ? searchForm.value.time : []
    if (range.length === 2 && range[0] && range[1]) {
      params.startTime = range[0]
      params.endTime = range[1]
    }
    // 其余列走通用 filters（模块多选、对象/动作/详情模糊）
    const filters = buildFilters(searchForm.value, {
      module: 'in',
      target: 'like',
      action: 'like',
      detail: 'like'
    })
    if (filters) {
      params.filters = filters
    }
    replaceSearchParams(params)
    fetchData()
  }

  /** 重置：清空全部表头条件 */
  const handleReset = () => {
    searchForm.value = {}
    resetSearchParams()
  }

  onMounted(async () => {
    // 前端模式守卫不拉取真实用户信息：已登录时主动刷新一次（权限判定用）
    if (userStore.accessToken) {
      try {
        const info = await fetchGetUserInfo()
        userStore.setUserInfo(info)
      } catch {
        // 忽略：刷新失败时保持现有权限判定
      }
    }
  })
</script>
