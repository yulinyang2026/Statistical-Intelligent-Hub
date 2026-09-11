<!-- 个人中心（2026-09-10 用户需求）：本人基本信息 + 更换头像 + 修改密码 -->
<template>
  <div class="page-content">
    <!-- 头部 -->
    <div class="flex items-center gap-3">
      <ElButton circle v-ripple @click="goBack">
        <ArtSvgIcon icon="ri:arrow-left-line" />
      </ElButton>
      <h2 class="m-0 text-lg font-semibold">{{ t('system.userCenter.title') }}</h2>
      <span class="text-xs text-g-500">{{ t('system.userCenter.profileTip') }}</span>
    </div>

    <div class="mt-4 grid grid-cols-1 gap-3 lg:grid-cols-3">
      <!-- 左：个人信息卡（头像可更换） -->
      <div class="art-card-sm p-4">
        <div class="flex flex-col items-center gap-3">
          <div class="group relative cursor-pointer" @click="triggerUpload">
            <ElAvatar :size="96" :src="profile?.avatar || defaultAvatar" />
            <div
              class="absolute inset-0 flex items-center justify-center rounded-full bg-black/40 opacity-0 transition-opacity duration-200 group-hover:opacity-100"
            >
              <ArtSvgIcon icon="ri:camera-line" class="text-xl text-white" />
            </div>
          </div>
          <div class="text-center">
            <div class="text-base font-semibold">{{ profile?.name || '—' }}</div>
            <div class="mt-1 text-xs text-g-500">
              {{ (profile?.roleNames ?? []).join('、') || '—' }}
            </div>
          </div>
          <div class="flex items-center gap-2">
            <ElButton size="small" v-ripple @click="triggerUpload">
              {{ t('system.userCenter.changeAvatar') }}
            </ElButton>
            <ElButton v-if="profile?.avatar" size="small" v-ripple @click="resetAvatar">
              {{ t('system.userCenter.resetAvatar') }}
            </ElButton>
          </div>
        </div>

        <ElDescriptions :column="1" border class="mt-4">
          <ElDescriptionsItem :label="t('system.userCenter.account')">
            {{ profile?.username || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.userCenter.employeeNo')">
            {{ profile?.employeeNo || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.userCenter.dept')">
            {{ (profile?.orgNames ?? []).join('、') || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.userCenter.mobile')">
            {{ profile?.mobile || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.userCenter.email')">
            {{ profile?.email || '—' }}
          </ElDescriptionsItem>
          <ElDescriptionsItem :label="t('system.userCenter.role')">
            {{ (profile?.roleNames ?? []).join('、') || '—' }}
          </ElDescriptionsItem>
        </ElDescriptions>
      </div>

      <!-- 右：修改密码 -->
      <div class="art-card-sm p-4 lg:col-span-2">
        <div class="mb-2 text-sm font-semibold">{{ t('system.userCenter.changePassword') }}</div>
        <ElAlert
          type="info"
          :closable="false"
          show-icon
          :title="t('system.userCenter.passwordRule')"
          class="mb-3 shrink-0"
        />
        <ArtForm
          ref="passwordFormRef"
          v-model="passwordForm"
          :items="passwordItems"
          :show-reset="false"
          :show-submit="false"
        />
        <div class="mt-2 flex justify-end">
          <ElButton type="primary" v-ripple :loading="saving" @click="submitPassword">
            {{ t('common.confirm') }}
          </ElButton>
        </div>
      </div>
    </div>

    <!-- 头像文件选择（隐藏） -->
    <input
      ref="fileInputRef"
      type="file"
      accept="image/png,image/jpeg,image/webp,image/gif"
      class="hidden"
      @change="handleFileChange"
    />
  </div>
</template>

<script setup lang="ts">
  import { fetchChangePassword, fetchUpdateAvatar, fetchUserProfile } from '@/api/user'
  import { fetchGetUserInfo } from '@/api/auth'
  import { useI18n } from 'vue-i18n'
  import { useRouter } from 'vue-router'
  import { useUserStore } from '@/store/modules/user'
  import defaultAvatar from '@imgs/user/avatar.webp'

  defineOptions({ name: 'UserCenter' })

  const { t } = useI18n()
  const router = useRouter()
  const userStore = useUserStore()

  const profile = ref<Api.User.UserProfile>()
  const saving = ref(false)

  const goBack = () => {
    router.back()
  }

  // ==================== 基本信息与头像 ====================

  const loadProfile = async () => {
    profile.value = await fetchUserProfile()
  }

  const fileInputRef = useTemplateRef<HTMLInputElement>('fileInputRef')

  const triggerUpload = () => {
    fileInputRef.value?.click()
  }

  /** 选择图片 → canvas 压缩为 128×128 → 上传 data URL */
  const handleFileChange = async (event: Event) => {
    const input = event.target as HTMLInputElement
    const file = input.files?.[0]
    input.value = ''
    if (!file) {
      return
    }
    if (!file.type.startsWith('image/')) {
      ElMessage.warning(t('system.userCenter.avatarTypeError'))
      return
    }
    if (file.size > 2 * 1024 * 1024) {
      ElMessage.warning(t('system.userCenter.avatarTooLarge'))
      return
    }
    const dataUrl = await compressImage(file)
    await fetchUpdateAvatar(dataUrl)
    ElMessage.success(t('system.userCenter.avatarUpdated'))
    await loadProfile()
    await refreshUserInfo()
  }

  /** 压缩：等比缩放到 128×128 内并转 JPEG（默认头像的 data URL 体积可控） */
  const compressImage = (file: File): Promise<string> =>
    new Promise((resolve, reject) => {
      const reader = new FileReader()
      reader.onload = () => {
        const image = new Image()
        image.onload = () => {
          const size = 128
          const scale = Math.min(size / image.width, size / image.height, 1)
          const canvas = document.createElement('canvas')
          canvas.width = Math.round(image.width * scale)
          canvas.height = Math.round(image.height * scale)
          const context = canvas.getContext('2d')
          if (!context) {
            reject(new Error('canvas unavailable'))
            return
          }
          context.drawImage(image, 0, 0, canvas.width, canvas.height)
          resolve(canvas.toDataURL('image/jpeg', 0.85))
        }
        image.onerror = () => reject(new Error('image load failed'))
        image.src = String(reader.result)
      }
      reader.onerror = () => reject(new Error('file read failed'))
      reader.readAsDataURL(file)
    })

  const resetAvatar = async () => {
    await fetchUpdateAvatar('')
    ElMessage.success(t('system.userCenter.avatarReset'))
    await loadProfile()
    await refreshUserInfo()
  }

  /** 同步用户信息（顶栏头像随 userStore.info 变化） */
  const refreshUserInfo = async () => {
    try {
      const info = await fetchGetUserInfo()
      userStore.setUserInfo(info)
    } catch {
      // 忽略：刷新失败不影响个人中心展示
    }
  }

  // ==================== 修改密码 ====================

  const passwordFormRef = useTemplateRef<{
    validate: () => Promise<boolean>
    reset: () => void
    getOutput: () => Record<string, any>
  }>('passwordFormRef')

  const passwordForm = ref<Record<string, any>>({})

  const passwordItems = computed(() => [
    {
      key: 'oldPassword',
      label: t('system.userCenter.oldPassword'),
      type: 'input',
      span: 12,
      props: {
        placeholder: t('system.userCenter.oldPasswordPlaceholder'),
        type: 'password',
        showPassword: true,
        autocomplete: 'off'
      }
    },
    {
      key: 'newPassword',
      label: t('system.userCenter.newPassword'),
      type: 'input',
      span: 12,
      props: {
        placeholder: t('system.userCenter.newPasswordPlaceholder'),
        type: 'password',
        showPassword: true,
        autocomplete: 'off'
      }
    },
    {
      key: 'confirmPassword',
      label: t('system.userCenter.confirmPassword'),
      type: 'input',
      span: 12,
      props: {
        placeholder: t('system.userCenter.confirmPasswordPlaceholder'),
        type: 'password',
        showPassword: true,
        autocomplete: 'off'
      }
    }
  ])

  const submitPassword = async () => {
    const { oldPassword, newPassword, confirmPassword } = passwordForm.value
    if (!oldPassword) {
      ElMessage.warning(t('system.userCenter.oldPasswordPlaceholder'))
      return
    }
    if (!newPassword) {
      ElMessage.warning(t('system.userCenter.newPasswordPlaceholder'))
      return
    }
    if (!/^(?=.*[A-Za-z])(?=.*\d).{8,}$/.test(newPassword)) {
      ElMessage.warning(t('system.userCenter.passwordRule'))
      return
    }
    if (newPassword !== confirmPassword) {
      ElMessage.warning(t('system.userCenter.passwordMismatch'))
      return
    }
    saving.value = true
    try {
      await fetchChangePassword({ oldPassword, newPassword })
      ElMessage.success(t('system.userCenter.passwordChanged'))
      passwordForm.value = {}
    } finally {
      saving.value = false
    }
  }

  onMounted(async () => {
    await loadProfile()
  })
</script>
