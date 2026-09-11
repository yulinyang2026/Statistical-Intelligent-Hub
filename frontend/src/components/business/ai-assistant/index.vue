<!-- 管理小帮手：可拖动悬浮入口 + 悬浮聊天窗（AI 对话；2026-09-10 由「统计智慧助手」更名，默认位置改为底部居中） -->
<template>
  <!-- 悬浮入口：椭圆胶囊（可拖动，颜色跟随主题色） -->
  <Transition
    enter-active-class="tad-300 ease-out"
    leave-active-class="tad-200 ease-in"
    enter-from-class="opacity-0 translate-y-2"
    enter-to-class="opacity-100 translate-y-0"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-2"
  >
    <button
      ref="pillRef"
      v-show="!panelVisible"
      type="button"
      class="fixed z-[100] flex-c gap-2 rounded-full px-4.5 py-2.5 bg-theme text-white text-sm shadow-lg c-p select-none touch-none tad-300 hover:shadow-xl"
      :style="{ left: pillX + 'px', top: pillY + 'px' }"
      @pointerdown="onPillPointerDown"
    >
      <ArtSvgIcon icon="ri:chat-smile-2-line" class="text-lg pointer-events-none" />
      <span class="pointer-events-none">{{ t('aiAssistant.name') }}</span>
    </button>
  </Transition>

  <!-- 悬浮聊天窗（跟随胶囊位置弹出） -->
  <Transition
    enter-active-class="tad-300 ease-out"
    leave-active-class="tad-200 ease-in"
    enter-from-class="opacity-0 translate-y-2"
    enter-to-class="opacity-100 translate-y-0"
    leave-from-class="opacity-100 translate-y-0"
    leave-to-class="opacity-0 translate-y-2"
  >
    <div
      v-show="panelVisible"
      class="fixed z-[100] art-card flex h-125 flex-col overflow-hidden"
      :style="panelStyle"
    >
      <!-- 头部 -->
      <div class="px-4 pt-3.5 flex-cb">
        <div>
          <span class="text-base font-medium text-g-900">{{ t('aiAssistant.name') }}</span>
          <div class="mt-1.5 flex-c gap-1">
            <div
              class="h-2 w-2 rounded-full"
              :class="isOnline ? 'bg-success/100' : 'bg-danger/100'"
            ></div>
            <span class="text-xs text-g-600">{{ isOnline ? t('aiAssistant.online') : t('aiAssistant.offline') }}</span>
          </div>
        </div>
        <div class="flex-c gap-2">
          <ElIcon
            class="c-p text-g-600 text-xl hover:text-g-900"
            :title="t('aiAssistant.newSession')"
            @click="createNewSession"
          >
            <Plus />
          </ElIcon>
          <ElIcon class="c-p text-g-600 text-xl hover:text-g-900" @click="closeChat">
            <Close />
          </ElIcon>
        </div>
      </div>

      <!-- 消息区域 -->
      <div
        ref="messageContainer"
        class="flex-1 overflow-y-auto border-t-d px-4 py-4 [&::-webkit-scrollbar]:!w-1"
      >
        <template v-for="message in messages" :key="message.id">
          <div
            :class="[
              'mb-6 flex w-full items-start gap-2',
              message.role === 'user' ? 'flex-row-reverse' : 'flex-row'
            ]"
          >
            <ElAvatar :size="32" :src="message.role === 'user' ? userAvatar : aiAvatar" class="shrink-0" />
            <div
              :class="[
                'flex max-w-[70%] flex-col',
                message.role === 'user' ? 'items-end' : 'items-start'
              ]"
            >
              <div
                :class="[
                  'rounded-md px-3.5 py-2.5 text-sm leading-[1.4] text-g-900',
                  message.role === 'user' ? 'bg-theme/15' : 'bg-g-300/50'
                ]"
              >
                <span v-if="message.streaming && !message.content">▍</span>
                <span v-else class="whitespace-pre-wrap">{{ message.content }}</span>
              </div>
              <span v-if="message.time" class="mt-1 text-xs text-g-600">{{ message.time }}</span>
            </div>
          </div>
        </template>
      </div>

      <!-- 输入区域 -->
      <div class="px-4 pb-4">
        <ElInput
          v-model="messageText"
          type="textarea"
          :rows="2"
          :placeholder="t('aiAssistant.inputPlaceholder')"
          resize="none"
          @keyup.enter.prevent="sendMessage"
        >
          <template #append>
            <ElButton type="primary" :loading="streaming" class="h-full" @click="sendMessage" v-ripple>
              {{ t('aiAssistant.send') }}
            </ElButton>
          </template>
        </ElInput>
      </div>
    </div>
  </Transition>
</template>

<script setup lang="ts">
  import { useI18n } from 'vue-i18n'
  import { Close, Plus } from '@element-plus/icons-vue'
  import { mittBus } from '@/utils/sys'
  import { useUserStore } from '@/store/modules/user'
  import { fetchChatSessions, fetchCreateChatSession, fetchChatMessages } from '@/api/chat'
  import userAvatar from '@/assets/images/avatar/avatar5.webp'
  import aiAvatar from '@/assets/images/avatar/avatar10.webp'

  defineOptions({ name: 'AiAssistant' })

  const { t } = useI18n()

  /** 展示用消息（含流式中的占位消息） */
  interface DisplayMessage {
    id: number
    role: 'user' | 'assistant'
    content: string
    time?: string
    streaming?: boolean
  }

  const MOBILE_BREAKPOINT = 640
  const PANEL_WIDTH = 360
  const PANEL_HEIGHT = 500
  const VIEWPORT_PADDING = 8
  const SCROLL_DELAY = 100

  const { width, height } = useWindowSize()
  const isMobile = computed(() => width.value < MOBILE_BREAKPOINT)

  // 组件状态
  const panelVisible = ref(false)
  const isOnline = ref(false)
  const inited = ref(false)
  const streaming = ref(false)

  // 会话与消息
  const sessionId = ref<number | null>(null)
  const messages = ref<DisplayMessage[]>([])
  const messageText = ref('')
  const messageContainer = ref<HTMLElement | null>(null)

  // ==================== 悬浮入口拖动 ====================

  const pillRef = ref<HTMLElement | null>(null)
  /** 胶囊位置（视口左上角坐标，初始左下角） */
  const pillX = ref(16)
  const pillY = ref(120)

  interface DragState {
    startX: number
    startY: number
    originX: number
    originY: number
    moved: boolean
  }

  let dragState: DragState | null = null

  const clamp = (value: number, min: number, max: number): number =>
    Math.min(Math.max(value, min), max)

  const clampPillPosition = (): void => {
    const el = pillRef.value
    if (!el) return
    const w = el.offsetWidth
    const h = el.offsetHeight
    pillX.value = clamp(pillX.value, VIEWPORT_PADDING, Math.max(VIEWPORT_PADDING, window.innerWidth - w - VIEWPORT_PADDING))
    pillY.value = clamp(pillY.value, VIEWPORT_PADDING, Math.max(VIEWPORT_PADDING, window.innerHeight - h - VIEWPORT_PADDING))
  }

  /** 按下开始拖动；位移小于阈值视为点击打开面板 */
  const onPillPointerDown = (event: PointerEvent): void => {
    if (event.button !== 0) return
    event.preventDefault()
    dragState = {
      startX: event.clientX,
      startY: event.clientY,
      originX: pillX.value,
      originY: pillY.value,
      moved: false
    }

    const onMove = (moveEvent: PointerEvent): void => {
      if (!dragState) return
      const dx = moveEvent.clientX - dragState.startX
      const dy = moveEvent.clientY - dragState.startY
      if (Math.abs(dx) + Math.abs(dy) > 3) dragState.moved = true
      if (!dragState.moved) return
      const el = pillRef.value
      const w = el?.offsetWidth ?? 0
      const h = el?.offsetHeight ?? 0
      pillX.value = clamp(dragState.originX + dx, VIEWPORT_PADDING, Math.max(VIEWPORT_PADDING, window.innerWidth - w - VIEWPORT_PADDING))
      pillY.value = clamp(dragState.originY + dy, VIEWPORT_PADDING, Math.max(VIEWPORT_PADDING, window.innerHeight - h - VIEWPORT_PADDING))
    }

    const onUp = (): void => {
      window.removeEventListener('pointermove', onMove)
      window.removeEventListener('pointerup', onUp)
      const wasDrag = dragState?.moved ?? false
      dragState = null
      if (!wasDrag) openChat()
    }

    window.addEventListener('pointermove', onMove)
    window.addEventListener('pointerup', onUp)
  }

  /** 聊天窗位置：跟随胶囊，在其上方展开并夹在视口内 */
  const panelStyle = computed(() => {
    const panelWidth = isMobile.value ? Math.min(PANEL_WIDTH, width.value - VIEWPORT_PADDING * 2) : PANEL_WIDTH
    const left = clamp(pillX.value, VIEWPORT_PADDING, Math.max(VIEWPORT_PADDING, width.value - panelWidth - VIEWPORT_PADDING))
    const bottom = Math.max(VIEWPORT_PADDING, height.value - pillY.value + 12)
    return { left: `${left}px`, bottom: `${bottom}px`, width: `${panelWidth}px`, height: `${PANEL_HEIGHT}px` }
  })

  // ==================== 会话初始化 ====================

  /** 打开面板时初始化：取最近会话（无则新建）并加载历史 */
  const initChat = async (): Promise<void> => {
    if (inited.value) return
    try {
      const sessions = await fetchChatSessions()
      if (sessions.length > 0) {
        sessionId.value = sessions[0].id
      } else {
        sessionId.value = await fetchCreateChatSession()
      }
      await reloadMessages()
      isOnline.value = true
      inited.value = true
      scrollToBottom()
    } catch {
      isOnline.value = false
      ElMessage.error(t('aiAssistant.streamError'))
    }
  }

  const reloadMessages = async (): Promise<void> => {
    if (sessionId.value == null) return
    const list = await fetchChatMessages(sessionId.value)
    messages.value = list.map((item) => ({
      id: item.id,
      role: item.role,
      content: item.content,
      time: item.createTime ? item.createTime.slice(11, 16) : undefined
    }))
  }

  const createNewSession = async (): Promise<void> => {
    try {
      sessionId.value = await fetchCreateChatSession()
      messages.value = []
      isOnline.value = true
      inited.value = true
    } catch {
      ElMessage.error(t('aiAssistant.streamError'))
    }
  }

  // ==================== 消息发送（SSE 流式） ====================

  const sendMessage = async (): Promise<void> => {
    const text = messageText.value.trim()
    if (!text || streaming.value) return
    messageText.value = ''

    try {
      if (sessionId.value == null) {
        sessionId.value = await fetchCreateChatSession()
      }

      // 乐观追加用户消息与 AI 占位
      messages.value.push({ id: Date.now(), role: 'user', content: text })
      const assistant: DisplayMessage = { id: Date.now() + 1, role: 'assistant', content: '', streaming: true }
      messages.value.push(assistant)
      scrollToBottom()

      streaming.value = true
      await streamChat(sessionId.value, text, assistant)
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : t('aiAssistant.streamError'))
    } finally {
      streaming.value = false
      try {
        await reloadMessages()
      } catch {
        // 忽略刷新失败，界面保留本地累积内容
      }
      scrollToBottom()
    }
  }

  /** 调用 SSE 流式接口，把增量 token 追加到占位消息 */
  const streamChat = async (id: number, content: string, target: DisplayMessage): Promise<void> => {
    const { accessToken } = useUserStore()
    const response = await fetch(`/api/chat/sessions/${id}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken ? { Authorization: accessToken } : {})
      },
      body: JSON.stringify({ content })
    })

    const contentType = response.headers.get('content-type') ?? ''
    if (!response.ok || !contentType.includes('text/event-stream')) {
      const body = await response.json().catch(() => null)
      throw new Error(body?.msg || t('aiAssistant.streamError'))
    }

    const reader = response.body!.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    const handleEvent = (event: string): void => {
      for (const line of event.split('\n')) {
        if (!line.startsWith('data:')) continue
        const payload = line.slice(5).trim()
        if (payload === '[DONE]') return
        let data: { content?: string; error?: string } | null = null
        try {
          data = JSON.parse(payload)
        } catch {
          continue
        }
        if (data?.error) throw new Error(data.error)
        if (data?.content) {
          target.content += data.content
          scrollToBottom()
        }
      }
    }
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const events = buffer.split('\n\n')
      buffer = events.pop() ?? ''
      events.forEach(handleEvent)
    }
    // 流结束时处理缓冲区残留的最后一个事件
    if (buffer.trim()) handleEvent(buffer)
  }

  const scrollToBottom = (): void => {
    nextTick(() => {
      setTimeout(() => {
        if (messageContainer.value) {
          messageContainer.value.scrollTop = messageContainer.value.scrollHeight
        }
      }, SCROLL_DELAY)
    })
  }

  // ==================== 开关控制 ====================

  const openChat = (): void => {
    panelVisible.value = true
    initChat()
  }

  const closeChat = (): void => {
    panelVisible.value = false
  }

  onMounted(() => {
    mittBus.on('openChat', openChat)
    // 2026-09-10 用户需求：默认定位到页面底部居中（原为左下角；仍可自由拖动，视口内夹取）
    nextTick(() => {
      const el = pillRef.value
      if (el) {
        pillX.value = clamp(
          (window.innerWidth - el.offsetWidth) / 2,
          VIEWPORT_PADDING,
          Math.max(VIEWPORT_PADDING, window.innerWidth - el.offsetWidth - VIEWPORT_PADDING)
        )
        pillY.value = clamp(window.innerHeight - el.offsetHeight - 16, VIEWPORT_PADDING, window.innerHeight - el.offsetHeight - VIEWPORT_PADDING)
      }
    })
  })

  onUnmounted(() => {
    mittBus.off('openChat', openChat)
  })

  // 窗口缩放后把胶囊夹回视口内
  watch([width, height], () => {
    clampPillPosition()
  })
</script>
