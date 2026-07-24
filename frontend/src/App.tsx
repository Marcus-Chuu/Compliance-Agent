import { FormEvent, KeyboardEvent, useEffect, useRef, useState } from 'react'
import {
  BookOpenText,
  Bot,
  CircleStop,
  Landmark,
  MessageSquareText,
  Plus,
  SendHorizontal,
  ShieldCheck,
  Sparkles,
  UserRound,
} from 'lucide-react'
import { streamChat } from './api/chat'
import type { ChatMessage, ChatStreamEvent } from './types'

const suggestions = [
  '申请授信的客户需要满足哪些准入条件？',
  '贷款 100 万元，年利率 4.2%，期限 3 年，月供和总利息是多少？',
  '请准确查找第十条的制度原文，并说明审批权限。',
]

function createId(): string {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random().toString(16).slice(2)}`
}

function App() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [chatId, setChatId] = useState(createId)
  const [streaming, setStreaming] = useState(false)
  const abortControllerRef = useRef<AbortController | null>(null)
  const messagesEndRef = useRef<HTMLDivElement | null>(null)
  const textareaRef = useRef<HTMLTextAreaElement | null>(null)

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: streaming ? 'auto' : 'smooth' })
  }, [messages, streaming])

  useEffect(() => {
    const textarea = textareaRef.current
    if (!textarea) return
    textarea.style.height = 'auto'
    textarea.style.height = `${Math.min(textarea.scrollHeight, 160)}px`
  }, [input])

  useEffect(() => () => abortControllerRef.current?.abort(), [])

  const updateAssistantMessage = (
    messageId: string,
    updater: (message: ChatMessage) => ChatMessage,
  ) => {
    setMessages((current) =>
      current.map((message) => (message.id === messageId ? updater(message) : message)),
    )
  }

  const ask = async (rawQuestion: string) => {
    const question = rawQuestion.trim()
    if (!question || streaming) return

    const assistantMessageId = createId()
    const controller = new AbortController()
    abortControllerRef.current = controller
    setInput('')
    setStreaming(true)
    setMessages((current) => [
      ...current,
      {
        id: createId(),
        role: 'user',
        content: question,
        createdAt: new Date(),
        status: 'complete',
      },
      {
        id: assistantMessageId,
        role: 'assistant',
        content: '',
        createdAt: new Date(),
        status: 'streaming',
      },
    ])

    try {
      await streamChat({
        message: question,
        chatId,
        signal: controller.signal,
        onEvent: (event: ChatStreamEvent) => {
          if (event.chatId && event.chatId !== chatId) {
            setChatId(event.chatId)
          }
          if (event.type === 'message') {
            updateAssistantMessage(assistantMessageId, (message) => ({
              ...message,
              content: message.content + event.content,
            }))
          }
          if (event.type === 'done') {
            updateAssistantMessage(assistantMessageId, (message) => ({
              ...message,
              status: 'complete',
            }))
          }
        },
      })
    } catch (error) {
      const aborted = controller.signal.aborted
      const errorMessage = error instanceof Error ? error.message : '请求失败，请稍后重试'
      updateAssistantMessage(assistantMessageId, (message) => ({
        ...message,
        content: message.content || (aborted ? '已停止生成。' : errorMessage),
        status: aborted ? 'stopped' : 'error',
      }))
    } finally {
      abortControllerRef.current = null
      setStreaming(false)
    }
  }

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    void ask(input)
  }

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault()
      void ask(input)
    }
  }

  const startNewConversation = () => {
    abortControllerRef.current?.abort()
    setMessages([])
    setChatId(createId())
    setInput('')
    textareaRef.current?.focus()
  }

  return (
    <div className="app-shell">
      <header className="topbar">
        <div className="brand">
          <div className="brand-mark" aria-hidden="true">
            <Landmark size={21} strokeWidth={1.8} />
          </div>
          <div>
            <div className="brand-name">智策</div>
            <div className="brand-subtitle">智能合规问答</div>
          </div>
        </div>

        <div className="header-actions">
          <div className="knowledge-status" title="内部制度知识库已连接">
            <span className="status-dot" />
            知识库就绪
          </div>
          <button className="new-chat-button" type="button" onClick={startNewConversation}>
            <Plus size={17} />
            新对话
          </button>
        </div>
      </header>

      <main className="chat-layout">
        <section className="conversation" aria-live="polite">
          {messages.length === 0 ? (
            <div className="welcome">
              <div className="welcome-icon" aria-hidden="true">
                <ShieldCheck size={31} strokeWidth={1.5} />
              </div>
              <p className="eyebrow">INTERNAL KNOWLEDGE ASSISTANT</p>
              <h1>让制度查询与业务判断更清晰</h1>
              <p className="welcome-copy">
                基于内部制度知识库提供语义检索、条款核验与贷款测算。请描述你的业务问题，我会给出有依据的回答。
              </p>

              <div className="capability-row" aria-label="系统能力">
                <span><BookOpenText size={16} />制度语义检索</span>
                <span><MessageSquareText size={16} />条款精确核验</span>
                <span><Sparkles size={16} />贷款测算</span>
              </div>

              <div className="suggestions">
                <p>你可以这样问</p>
                {suggestions.map((suggestion) => (
                  <button key={suggestion} type="button" onClick={() => void ask(suggestion)}>
                    <span>{suggestion}</span>
                    <SendHorizontal size={16} aria-hidden="true" />
                  </button>
                ))}
              </div>
            </div>
          ) : (
            <div className="message-list">
              {messages.map((message) => (
                <article className={`message message-${message.role}`} key={message.id}>
                  <div className="avatar" aria-hidden="true">
                    {message.role === 'assistant' ? <Bot size={18} /> : <UserRound size={18} />}
                  </div>
                  <div className="message-body">
                    <div className="message-meta">
                      <strong>{message.role === 'assistant' ? '智能合规助手' : '你'}</strong>
                      <time>{message.createdAt.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })}</time>
                    </div>
                    <div className={`message-content status-${message.status}`}>
                      {message.content || (
                        <span className="thinking"><i /><i /><i />正在检索并生成回答</span>
                      )}
                    </div>
                    {message.status === 'error' && <span className="message-state">本次回答未完成</span>}
                    {message.status === 'stopped' && <span className="message-state">生成已停止</span>}
                  </div>
                </article>
              ))}
              <div ref={messagesEndRef} />
            </div>
          )}
        </section>

        <div className="composer-area">
          <form className="composer" onSubmit={handleSubmit}>
            <textarea
              ref={textareaRef}
              value={input}
              onChange={(event) => setInput(event.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="输入制度、合规或贷款测算问题…"
              aria-label="向智能合规助手提问"
              rows={1}
              maxLength={8000}
              disabled={streaming}
            />
            {streaming ? (
              <button
                className="send-button stop-button"
                type="button"
                onClick={() => abortControllerRef.current?.abort()}
                aria-label="停止生成"
              >
                <CircleStop size={20} />
              </button>
            ) : (
              <button
                className="send-button"
                type="submit"
                disabled={!input.trim()}
                aria-label="发送问题"
              >
                <SendHorizontal size={20} />
              </button>
            )}
          </form>
          <p className="composer-hint">Enter 发送 · Shift + Enter 换行 · 回答仅供内部制度检索辅助</p>
        </div>
      </main>
    </div>
  )
}

export default App
