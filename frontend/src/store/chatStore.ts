import { createStore } from 'zustand'
import type { Message } from '@/types'

export interface ChatState {
  sessionId: string | null
  messages: Message[]
  isStreaming: boolean
  streamingContent: string
  setSessionId: (id: string) => void
  addMessage: (message: Message) => void
  startStreaming: () => void
  appendChunk: (chunk: string) => void
  finishStreaming: () => void
  clearMessages: () => void
}

export const createChatStore = () =>
  createStore<ChatState>()((set) => ({
    sessionId: null,
    messages: [],
    isStreaming: false,
    streamingContent: '',
    setSessionId: (id) => set({ sessionId: id }),
    addMessage: (message) =>
      set((state) => ({ messages: [...state.messages, message] })),
    startStreaming: () =>
      set({ isStreaming: true, streamingContent: '' }),
    appendChunk: (chunk) =>
      set((state) => ({ streamingContent: state.streamingContent + chunk })),
    finishStreaming: () =>
      set((state) => ({
        isStreaming: false,
        messages: [
          ...state.messages,
          {
            id: `msg-${Date.now()}`,
            role: 'assistant',
            content: state.streamingContent,
            timestamp: new Date().toISOString(),
          },
        ],
        streamingContent: '',
      })),
    clearMessages: () =>
      set({ messages: [], streamingContent: '' }),
  }))
