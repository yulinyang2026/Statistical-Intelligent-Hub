import request from '@/utils/http'

/**
 * 管理小帮手（AI 对话）接口
 */
export function fetchChatSessions() {
  return request.get<Api.Ai.ChatSessionItem[]>({ url: '/api/chat/sessions' })
}

export function fetchCreateChatSession() {
  return request.post<number>({ url: '/api/chat/sessions' })
}

export function fetchChatMessages(sessionId: number) {
  return request.get<Api.Ai.ChatMessageItem[]>({ url: `/api/chat/sessions/${sessionId}/messages` })
}
