<!-- 高级查询抽屉：多字段组合查询，使用 ArtForm 渲染 -->
<template>
  <ElDrawer
    v-model="visible"
    :title="t('demo.user.advancedSearch')"
    size="640px"
    :close-on-click-modal="false"
    append-to-body
    @closed="handleClosed"
  >
    <ArtForm
      ref="formRef"
      v-model="formData"
      :items="advancedItems"
      :span="12"
      :gutter="16"
      label-width="90px"
      :show-submit="false"
      :show-reset="false"
    />
    <template #footer>
      <ElButton v-ripple @click="handleReset">
        {{ t('table.searchBar.reset') }}
      </ElButton>
      <ElButton type="primary" v-ripple @click="handleSearch">
        {{ t('table.searchBar.search') }}
      </ElButton>
    </template>
  </ElDrawer>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'DemoUserAdvancedSearchDrawer' })

  interface Emits {
    /** 提交高级查询条件（已映射为接口参数） */
    (e: 'search', params: Record<string, any>): void
    /** 重置高级查询条件 */
    (e: 'reset'): void
  }

  const emit = defineEmits<Emits>()

  const { t } = useI18n()

  const visible = defineModel<boolean>('visible', { default: false })

  // ArtForm 是全局注册组件（模板可直接用），但脚本里取不到它的类型，
  // 因此用结构化类型声明 ref，避免 import 组件导致多余依赖
  const formRef = useTemplateRef<{
    getOutput: () => Record<string, any>
    reset: () => void
  }>('formRef')

  /** 高级查询表单数据 */
  const formData = ref<Record<string, any>>({})

  /** 部门选项（Mock 数据域值） */
  const deptOptions = ['研发部', '产品部', '运营部', '市场部', '财务部', '人力资源部']

  /** 角色选项（Mock 数据域值） */
  const roleOptions = ['admin', 'editor', 'viewer', 'auditor']

  /** 高级查询表单项（多字段组合，双列布局） */
  const advancedItems = computed(() => [
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
      key: 'gender',
      label: t('demo.user.gender'),
      type: 'select',
      props: {
        placeholder: t('demo.user.genderPlaceholder'),
        clearable: true,
        options: [
          { label: t('demo.user.male'), value: '1' },
          { label: t('demo.user.female'), value: '2' }
        ]
      }
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
    },
    {
      key: 'dept',
      label: t('demo.user.dept'),
      type: 'select',
      props: {
        placeholder: t('demo.user.deptPlaceholder'),
        clearable: true,
        filterable: true,
        options: deptOptions.map((item) => ({ label: item, value: item }))
      }
    },
    {
      key: 'role',
      label: t('demo.user.role'),
      type: 'select',
      props: {
        placeholder: t('demo.user.rolePlaceholder'),
        clearable: true,
        options: roleOptions.map((item) => ({ label: item, value: item }))
      }
    },
    {
      key: 'phone',
      label: t('demo.user.phone'),
      type: 'input',
      props: { placeholder: t('demo.user.phonePlaceholder'), clearable: true, maxlength: 11 }
    },
    {
      key: 'email',
      label: t('demo.user.email'),
      type: 'input',
      props: { placeholder: t('demo.user.emailPlaceholder'), clearable: true, maxlength: 50 }
    },
    {
      key: 'ageMin',
      label: t('demo.user.ageMin'),
      type: 'number',
      props: { placeholder: t('demo.user.ageMin'), min: 18, max: 60, controlsPosition: 'right' }
    },
    {
      key: 'ageMax',
      label: t('demo.user.ageMax'),
      type: 'number',
      props: { placeholder: t('demo.user.ageMax'), min: 18, max: 60, controlsPosition: 'right' }
    },
    {
      key: 'createTimeRange',
      label: t('demo.user.createTimeRange'),
      span: 24,
      type: 'daterange',
      props: {
        type: 'daterange',
        valueFormat: 'YYYY-MM-DD',
        startPlaceholder: t('demo.user.startTime'),
        endPlaceholder: t('demo.user.endTime')
      }
    }
  ])

  /**
   * 提交高级查询：读取清洗后的表单输出，
   * 将时间范围字段映射为接口的 startTime / endTime
   */
  const handleSearch = () => {
    const output = formRef.value?.getOutput() ?? {}
    const { createTimeRange, ...rest } = output

    const params: Record<string, any> = { ...rest }
    if (Array.isArray(createTimeRange) && createTimeRange.length === 2) {
      params.startTime = createTimeRange[0]
      params.endTime = createTimeRange[1]
    }

    visible.value = false
    emit('search', params)
  }

  /** 重置高级查询表单 */
  const handleReset = () => {
    formRef.value?.reset()
    formData.value = {}
    emit('reset')
  }

  /** 抽屉关闭后清空表单（下次打开为空白条件） */
  const handleClosed = () => {
    formData.value = {}
  }

  /**
   * 打开抽屉
   * @param initial 回填的查询条件（接口参数形态，来自上次已生效的高级筛选）
   */
  const open = (initial?: Record<string, any>) => {
    if (!initial) {
      formData.value = {}
    } else {
      // 接口参数 startTime / endTime 还原为表单字段 createTimeRange
      const { startTime, endTime, ...rest } = initial
      formData.value = { ...rest }
      if (startTime && endTime) {
        formData.value.createTimeRange = [startTime, endTime]
      }
    }
    visible.value = true
  }

  defineExpose({ open })
</script>
