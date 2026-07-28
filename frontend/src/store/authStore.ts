import { createStore } from 'zustand'
import type { AuthUser } from '@/types'

export interface AuthState {
  user: AuthUser | null
  accessToken: string | null
  tenantId: string | null
  setAuth: (user: AuthUser, token: string, tenantId: string) => void
  setPlan: (plan: AuthUser["plan"]) => void
  clearAuth: () => void
}

export const createAuthStore = () =>
  createStore<AuthState>()((set) => ({
    user: null,
    accessToken: null,
    tenantId: null,
    setAuth: (user, accessToken, tenantId) =>
      set({ user, accessToken, tenantId }),
    setPlan: (plan) => set((state) => state.user?.plan === plan ? state : ({
      user: state.user ? { ...state.user, plan } : null,
    })),
    clearAuth: () =>
      set({ user: null, accessToken: null, tenantId: null }),
  }))
