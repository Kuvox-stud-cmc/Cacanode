import { createStore } from 'zustand'

export interface UIState {
  sidebarOpen: boolean
  activeModal: string | null
  modalData: unknown
  toggleSidebar: () => void
  openModal: (name: string, data?: unknown) => void
  closeModal: () => void
}

export const createUIStore = () =>
  createStore<UIState>()((set) => ({
    sidebarOpen: true,
    activeModal: null,
    modalData: null,
    toggleSidebar: () =>
      set((state) => ({ sidebarOpen: !state.sidebarOpen })),
    openModal: (name, data = null) =>
      set({ activeModal: name, modalData: data }),
    closeModal: () =>
      set({ activeModal: null, modalData: null }),
  }))
