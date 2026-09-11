<!-- 文档新增 / 编辑 / 只读预览弹窗（FR-DOC-003/004：Markdown 编辑预览双模式，保存后自动进入预览） -->
<template>
  <ElDialog
    v-model="visible"
    :title="dialogTitle"
    width="860px"
    :close-on-click-modal="false"
    append-to-body
    @closed="handleClosed"
  >
    <!-- 只读预览标识（FR-DOC-006） -->
    <ElAlert
      v-if="isView"
      type="info"
      :closable="false"
      show-icon
      :title="t('system.doc.readOnlyPreview')"
      class="mb-3"
    />

    <ElForm label-width="80px" @submit.prevent>
      <div class="flex flex-wrap items-start gap-2">
        <ElFormItem :label="t('system.doc.title')" required class="m-0 flex-1" style="min-width: 240px">
          <ElInput
            v-model="formData.title"
            :placeholder="t('system.doc.titlePlaceholder')"
            :disabled="isView"
            maxlength="128"
          />
        </ElFormItem>
        <ElFormItem
          :label="t('system.doc.belong')"
          required
          class="m-0 flex-1"
          style="min-width: 240px"
        >
          <ElSelect
            v-model="formData.belong"
            :placeholder="t('system.doc.belongPlaceholder')"
            :disabled="isView || !!presetBelong"
            clearable
            filterable
          >
            <ElOption
              v-for="option in belongOptions"
              :key="option.value"
              :label="option.label"
              :value="option.value"
            />
          </ElSelect>
        </ElFormItem>
      </div>
    </ElForm>

    <MarkdownEditor
      ref="editorRef"
      v-model="formData.content"
      :readonly="isView"
      class="mt-3"
    />

    <template #footer>
      <ElButton @click="visible = false">{{ t('common.cancel') }}</ElButton>
      <ElButton v-if="!isView" type="primary" v-ripple @click="handleSubmit">
        {{ t('common.confirm') }}
      </ElButton>
    </template>
  </ElDialog>
</template>

<script setup lang="ts">
  import { fetchCreateDoc, fetchDocDetail, fetchUpdateDoc } from '@/api/doc'
  import { fetchTaskOptions } from '@/api/task'
  import MarkdownEditor from '@/components/business/markdown-editor/index.vue'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'DocDialog' })

  const emit = defineEmits<{ success: [] }>()

  const { t } = useI18n()

  const visible = defineModel<boolean>('visible', { default: false })

  const isEdit = ref(false)
  const isView = ref(false)
  const editingId = ref<string>()
  /** 预置归属（模块详情 Tab 进入时锁定为该模块） */
  const presetBelong = ref<{ type: string; id: string; name: string }>()
  const formData = ref<{ title: string; belong: string; content: string }>({ title: '', belong: '', content: '' })

  const editorRef = useTemplateRef<{ preview: () => void }>('editorRef')

  /** 归属候选（项目/模块/专题） */
  const belongOptions = ref<{ label: string; value: string }[]>([])

  const dialogTitle = computed(() =>
    isView.value ? t('system.doc.viewTitle') : isEdit.value ? t('common.edit') : t('common.add')
  )

  const belongTypeLabel = (type: string) => {
    switch (type) {
      case 'project':
        return t('system.task.belongProject')
      case 'module':
        return t('system.task.belongModule')
      default:
        return t('system.task.belongTopic')
    }
  }

  /**
   * 打开弹窗：
   * - row：编辑 / 只读预览（view=true）
   * - presetBelong：从模块详情 Tab 新增（归属锁定）
   */
  const open = async (options: {
    row?: Api.Doc.DocListItem
    view?: boolean
    presetBelong?: { type: string; id: string; name: string }
  }) => {
    isEdit.value = !!options?.row
    isView.value = !!options?.view
    editingId.value = options?.row?.id
    presetBelong.value = options?.presetBelong

    const opts = await fetchTaskOptions()
    belongOptions.value = opts.belongOptions.map((option) => ({
      label: `${belongTypeLabel(option.type)}·${option.name}`,
      value: `${option.type}:${option.id}`
    }))

    if (options?.row) {
      const belong =
        options.row.projectId !== null
          ? `project:${options.row.projectId}`
          : options.row.moduleId !== null
            ? `module:${options.row.moduleId}`
            : options.row.topicId !== null
              ? `topic:${options.row.topicId}`
              : ''
      formData.value = { title: options.row.title, belong, content: '' }
      // 编辑/预览需拉取正文
      const detail = await fetchDocDetail(options.row.id)
      formData.value.content = detail.content
      if (isView.value) {
        nextTick(() => editorRef.value?.preview())
      }
    } else if (presetBelong.value) {
      formData.value = { title: '', belong: `${presetBelong.value.type}:${presetBelong.value.id}`, content: '' }
    } else {
      formData.value = { title: '', belong: '', content: '' }
    }
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = { title: '', belong: '', content: '' }
    presetBelong.value = undefined
  }

  const handleSubmit = async () => {
    const { title, belong, content } = formData.value
    if (!title?.trim()) {
      ElMessage.warning(t('system.doc.titlePlaceholder'))
      return
    }
    if (!belong) {
      ElMessage.warning(t('system.doc.belongRequired'))
      return
    }
    const [type, id] = belong.split(':')
    const payload: Api.Doc.DocSaveParams = {
      id: editingId.value,
      title: title.trim(),
      projectId: type === 'project' ? id : null,
      moduleId: type === 'module' ? id : null,
      topicId: type === 'topic' ? id : null,
      content: content ?? ''
    }
    if (isEdit.value && editingId.value) {
      await fetchUpdateDoc(editingId.value, payload)
    } else {
      await fetchCreateDoc(payload)
    }
    ElMessage.success(t('common.saveSuccess'))
    // 保存后自动进入预览模式（FR-DOC-004）
    editorRef.value?.preview()
    emit('success')
  }

  defineExpose({ open })
</script>
