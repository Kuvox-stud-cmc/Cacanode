// components/providers/StoreProvider.tsx
'use client'

import { createContext, useContext, useState, type ReactNode } from 'react'
import { useStore } from 'zustand'
import { createAuthStore, type AuthState } from '@/store/authStore'
import { createChatStore, type ChatState } from '@/store/chatStore'
import { createUIStore, type UIState } from '@/store/uiStore'

type AuthStore = ReturnType<typeof createAuthStore>
type ChatStore = ReturnType<typeof createChatStore>
type UIStore = ReturnType<typeof createUIStore>

interface StoreContextValue {
  authStore: AuthStore
  chatStore: ChatStore
  uiStore: UIStore
}

const StoreContext = createContext<StoreContextValue | null>(null)

export function StoreProvider({ children }: { children: ReactNode }) {
  const [stores] = useState<StoreContextValue>(() => ({
    authStore: createAuthStore(),
    chatStore: createChatStore(),
    uiStore: createUIStore(),
  }))

  return (
    <StoreContext.Provider value={stores}>
      {children}
    </StoreContext.Provider>
  )
}

// ─── Hooks ───────────────────────────────────────────────────────────────────

function useStoreContext() {
  const context = useContext(StoreContext)
  if (!context) throw new Error('Must be used within StoreProvider')
  return context
}

export function useAuthStore<T>(selector: (state: AuthState) => T): T {
  return useStore(useStoreContext().authStore, selector)
}

export function useChatStore<T>(selector: (state: ChatState) => T): T {
  return useStore(useStoreContext().chatStore, selector)
}

export function useUIStore<T>(selector: (state: UIState) => T): T {
  return useStore(useStoreContext().uiStore, selector)
}
