<!-- 表头漏斗筛选（2026-09-10 用户需求：所有字段均带漏斗；关键词搜索 / 候选多选 / 数值≥ / 日期区间） -->
<template>
  <ElPopover
    v-model:visible="popoverVisible"
    trigger="click"
    placement="bottom-start"
    :width="width"
    :show-arrow="false"
    popper-class="header-filter-popover"
  >
    <template #reference>
      <!-- 表头触发区：字段名 + 漏斗图标（已筛选时图标跟随主题色） -->
      <div class="inline-flex items-center gap-1 cursor-pointer select-none">
        <span class="whitespace-nowrap">{{ label }}</span>
        <ArtSvgIcon
          icon="ri:filter-3-fill"
          class="text-base"
          :class="isActive ? 'text-theme' : 'text-g-600 hover:text-theme'"
        />
      </div>
    </template>

    <div class="flex flex-col gap-2">
      <!-- ① 文本：关键词搜索（回车/失焦/清空即查询） -->
      <ElInput
        v-if="resolvedMode === 'text'"
        v-model="keyword"
        size="small"
        clearable
        :placeholder="placeholder"
        @keyup.enter="applyKeyword"
        @change="applyKeyword"
        @clear="applyKeyword"
      />

      <!-- ② 数值：大于等于 -->
      <template v-else-if="resolvedMode === 'number'">
        <ElInputNumber
          v-model="numberValue"
          size="small"
          class="w-full"
          :min="0"
          :controls="false"
          :placeholder="placeholder"
          @change="applyNumber"
        />
        <span class="text-xs text-g-500">{{ t('common.gteHint') }}</span>
      </template>

      <!-- ③ 日期：区间（含起止两天） -->
      <template v-else-if="resolvedMode === 'daterange'">
        <ElDatePicker
          v-model="dateRange"
          type="daterange"
          size="small"
          class="w-full"
          value-format="YYYY-MM-DD"
          range-separator="~"
          start-placeholder=""
          end-placeholder=""
          @change="applyDateRange"
        />
        <span class="text-xs text-g-500">{{ t('common.rangeHint') }}</span>
      </template>

      <!-- ④ 候选：多选（勾选即查、面板不关闭，首项「全部」清空） -->
      <template v-else>
        <ElInput
          v-if="options.length > 8"
          v-model="optionKeyword"
          size="small"
          clearable
          :placeholder="placeholder"
        />
        <ul class="m-0 p-0 list-none max-h-60 overflow-y-auto">
          <li
            class="flex items-center px-2 py-1.5 text-sm rounded-md cursor-pointer hover:bg-g-200"
            :class="selected.length ? 'text-g-700' : 'text-theme font-medium'"
            @click="clearOptions"
          >
            {{ t('common.all') }}
          </li>
          <li
            v-for="item in visibleOptions"
            :key="item.value"
            class="mt-0.5 px-2 py-0.5 rounded-md cursor-pointer hover:bg-g-200"
            @click="toggleOption(item.value, !isSelected(item.value))"
          >
            <ElCheckbox
              :model-value="isSelected(item.value)"
              class="w-full [&_.el-checkbox__label]:text-sm"
              :class="isSelected(item.value) ? '[&_.el-checkbox__label]:!text-theme' : ''"
              @click.stop
              @change="(checked: CheckboxValueType) => toggleOption(item.value, !!checked)"
            >
              {{ item.label }}
            </ElCheckbox>
          </li>
        </ul>
      </template>
    </div>
  </ElPopover>
</template>

<script setup lang="ts">
  import { ElPopover, type CheckboxValueType } from 'element-plus'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'HeaderFilter' })

  type FilterMode = 'auto' | 'text' | 'options' | 'number' | 'daterange'

  const props = withDefaults(
    defineProps<{
      /** 列头字段名（照常显示） */
      label: string
      /** 当前筛选值（v-model：文本/数值为 string，候选为 string[]，日期区间为 [start, end]） */
      modelValue?: string | string[] | null
      /** 候选值（枚举列；为空则为关键词搜索形态） */
      options?: { label: string; value: string }[]
      /** 面板形态：auto = 有候选走候选、无候选走文本 */
      mode?: FilterMode
      /** 输入框占位文案 */
      placeholder?: string
      /** 面板宽度 */
      width?: number
    }>(),
    {
      modelValue: '',
      options: () => [],
      mode: 'auto',
      placeholder: '',
      width: 220
    }
  )

  const emit = defineEmits<{
    (e: 'update:modelValue', value: string | string[]): void
    /** 筛选值变更（父级据此触发服务端查询） */
    (e: 'change'): void
  }>()

  const { t } = useI18n()

  const popoverVisible = ref(false)
  const keyword = ref<string>('')
  const optionKeyword = ref<string>('')
  const numberValue = ref<number | undefined>(undefined)
  const dateRange = ref<[string, string] | undefined>(undefined)

  const resolvedMode = computed<Exclude<FilterMode, 'auto'>>(() => {
    if (props.mode !== 'auto') return props.mode
    return props.options.length > 0 ? 'options' : 'text'
  })

  /** 已选候选（统一为数组，兼容外部传入单值） */
  const selected = computed<string[]>(() =>
    Array.isArray(props.modelValue) && resolvedMode.value === 'options'
      ? props.modelValue
      : props.modelValue
        ? [props.modelValue as string]
        : []
  )
  const isActive = computed(() => {
    const value = props.modelValue
    if (Array.isArray(value)) return value.length > 0
    return value !== undefined && value !== null && value !== ''
  })
  const isSelected = (value: string) => selected.value.includes(value)

  /** 面板打开时同步当前筛选值 */
  watch(popoverVisible, (visible) => {
    if (!visible) return
    optionKeyword.value = ''
    if (resolvedMode.value === 'text') {
      keyword.value = Array.isArray(props.modelValue) ? '' : (props.modelValue ?? '')
    } else if (resolvedMode.value === 'number') {
      const raw = Array.isArray(props.modelValue) ? props.modelValue[0] : props.modelValue
      numberValue.value = raw === undefined || raw === null || raw === '' ? undefined : Number(raw)
    } else if (resolvedMode.value === 'daterange') {
      dateRange.value =
        Array.isArray(props.modelValue) && props.modelValue.length === 2
          ? [props.modelValue[0], props.modelValue[1]]
          : undefined
    }
  })

  const visibleOptions = computed(() => {
    const kw = optionKeyword.value.trim()
    if (!kw) return props.options
    return props.options.filter((item) => item.label.includes(kw))
  })

  const applyKeyword = () => {
    emit('update:modelValue', keyword.value.trim())
    popoverVisible.value = false
    emit('change')
  }

  /** 数值：大于等于（清空即取消该列筛选） */
  const applyNumber = () => {
    emit('update:modelValue', numberValue.value === undefined || numberValue.value === null ? '' : `${numberValue.value}`)
    emit('change')
  }

  /** 日期区间：含起止两天 */
  const applyDateRange = () => {
    const range = dateRange.value
    if (!range || !range[0] || !range[1]) {
      emit('update:modelValue', [])
    } else {
      emit('update:modelValue', [range[0], range[1]])
    }
    popoverVisible.value = false
    emit('change')
  }

  /** 多选：勾选/取消勾选（面板保持打开，便于连续勾选） */
  const toggleOption = (value: string, checked: boolean) => {
    const next = checked ? [...selected.value, value] : selected.value.filter((item) => item !== value)
    emit('update:modelValue', next)
    emit('change')
  }

  /** 全部 = 清空该列筛选 */
  const clearOptions = () => {
    emit('update:modelValue', [])
    popoverVisible.value = false
    emit('change')
  }
</script>
