export type MessageRole = 'user' | 'assistant'

export type MessageStatus = 'streaming' | 'complete' | 'error' | 'stopped'

export interface ChatMessage {
  id: string
  role: MessageRole
  content: string
  createdAt: Date
  status: MessageStatus
}

export interface ChatStreamEvent {
  type: 'message' | 'done' | 'error'
  content: string
  chatId: string
}
