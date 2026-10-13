import { defineStore } from 'pinia'
import { api, refreshCsrf } from '../api/client'
import type { User } from '../types'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as User | null, initialized: false }),
  getters: {
    isAdmin: (state) => state.user?.role === 'SYSTEM_ADMIN',
    canApprove: (state) => state.user?.role === 'SYSTEM_ADMIN' || state.user?.role === 'DEPT_ADMIN',
  },
  actions: {
    async initialize() {
      try { this.user = await api<User>('/auth/me') } catch { this.user = null }
      this.initialized = true
    },
    async login(username: string, password: string) {
      await refreshCsrf()
      this.user = await api<User>('/auth/login', { method: 'POST', body: JSON.stringify({ username, password }) })
      await refreshCsrf()
    },
    async logout() { try { await api('/auth/logout', { method: 'POST' }) } finally { this.user = null }; await refreshCsrf() },
  },
})
