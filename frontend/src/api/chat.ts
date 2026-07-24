import type { ChatStreamEvent } from '../types'

interface StreamChatOptions {
  message: string
  chatId: string
  signal: AbortSignal
  onEvent: (event: ChatStreamEvent) => void
}

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL?.replace(/\/$/, '') ?? ''

export async function streamChat({
  message,
  chatId,
  signal,
  onEvent,
}: StreamChatOptions): Promise<void> {
  const response = await fetch(`${API_BASE_URL}/api/assist/chat/stream`, {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ message, chatId }),
    signal,
  })

  if (!response.ok) {
    const detail = await response.text()
    throw new Error(detail || `请求失败（HTTP ${response.status}）`)
  }
  if (!response.body) {
    throw new Error('浏览器没有收到可读取的流式响应')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''
  let completed = false

  while (true) {
    const { done, value } = await reader.read()
    if (done) {
      buffer += decoder.decode()
      break
    }

    buffer += decoder.decode(value, { stream: true })
    buffer = buffer.replace(/\r\n/g, '\n')

    let boundary = buffer.indexOf('\n\n')
    while (boundary >= 0) {
      const eventBlock = buffer.slice(0, boundary)
      buffer = buffer.slice(boundary + 2)
      const event = parseEventBlock(eventBlock)

      if (event) {
        onEvent(event)
        if (event.type === 'done') {
          completed = true
        }
        if (event.type === 'error') {
          throw new Error(event.content || '回答生成失败')
        }
      }
      boundary = buffer.indexOf('\n\n')
    }
  }

  if (!completed && !signal.aborted) {
    throw new Error('流式连接提前结束，请重试')
  }
}

function parseEventBlock(block: string): ChatStreamEvent | null {
  const dataLines: string[] = []

  for (const line of block.split('\n')) {
    if (!line || line.startsWith(':')) {
      continue
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trimStart())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  try {
    return JSON.parse(dataLines.join('\n')) as ChatStreamEvent
  } catch {
    throw new Error('服务端返回了无法解析的 SSE 数据')
  }
}
