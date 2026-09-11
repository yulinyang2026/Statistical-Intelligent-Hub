<!-- 重置密码弹窗 -->
<template>
  <ElDialog
    v-model="visible"
    :title="t('system.user.resetPassword')"
    width="420px"
    :close-on-click-modal="false"
    append-to-body
    @closed="handleClosed"
  >
    <ElAlert
      type="warning"
      :closable="false"
      show-icon
      :title="t('system.user.resetPasswordTips')"
      class="mb-4"
    />
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
  import { fetchResetPassword } from '@/api/user'
  import { useI18n } from 'vue-i18n'

  defineOptions({ name: 'ResetPasswordDialog' })

  const { t } = useI18n()

  const visible = defineModel<boolean>('visible', { default: false })
  // ArtForm 是全局注册组件（模板可直接用），但脚本里取不到它的类型，
  // 因此用结构化类型声明 ref，避免 import 组件导致多余依赖
  const formRef = useTemplateRef<{
    validate: () => Promise<boolean>
    reset: () => void
    getOutput: () => Record<string, any>
  }>('formRef')

  const targetUser = ref<Api.User.UserListItem>()
  const formData = ref<Record<string, any>>({})

  const formItems = computed(() => [
    {
      key: 'password',
      label: t('system.user.password'),
      type: 'input',
      span: 24,
      props: {
        placeholder: t('system.user.passwordPlaceholder'),
        type: 'password',
        showPassword: true
      }
    }
  ])

    /** 必填项标记：ArtForm 根节点为 <ElForm v-bind="$attrs">，
   *  以此处的 rules 让 ElFormItem 自动渲染红色星号（不改 components/core） */
  const formRules = computed<Record<string, any>>(() => {
    const rules: Record<string, any> = {
      password: [{ required: true, message: t('system.user.passwordPlaceholder') }],
    }
    return rules
  })
  const open = (row: Api.User.UserListItem) => {
    targetUser.value = row
    formData.value = { password: '' }
    visible.value = true
  }

  const handleClosed = () => {
    formData.value = {}
  }

  const handleSubmit = async () => {
    // 必填校验不通过时 ElForm.validate() 会 reject：捕获后提前返回，由表单项展示行内提示
    const valid = await Promise.resolve(formRef.value?.validate()).catch(() => false)
    if (valid === false) return
    if (!formData.value.password) {
      ElMessage.warning(t('system.user.passwordPlaceholder'))
      return
    }
    if (!targetUser.value) return
    await fetchResetPassword({ userId: targetUser.value.id, password: formData.value.password })
    ElMessage.success(t('common.operateSuccess'))
    visible.value = false
  }

  defineExpose({ open })
</script>
