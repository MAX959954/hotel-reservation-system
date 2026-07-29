import { defineStore } from 'pinia'
import { authApi } from '../api/auth'
import type { LogInRequest, RegisterRequest, Role } from '../types/auth'

const STORAGE_KEY = 'hotel-auth'

interface AuthState {
  token: string | null
  tokenType: string
  email: string | null
  roles: Role[]
}

function loadState(): AuthState {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return { token: null, tokenType: 'Bearer', email: null, roles: [] }
  try {
    return JSON.parse(raw) as AuthState
  } catch {
    return { token: null, tokenType: 'Bearer', email: null, roles: [] }
  }
}

export const useAuthStore = defineStore('auth', {
  state: (): AuthState => loadState(),

  getters: {
    isAuthenticated: (state) => !!state.token,
    hasRole: (state) => (role: Role) => state.roles.includes(role),
  },

  actions: {
    persist() {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          token: this.token,
          tokenType: this.tokenType,
          email: this.email,
          roles: this.roles,
        }),
      )
    },

    async login(payload: LogInRequest) {
      const res = await authApi.login(payload)
      this.token = res.token
      this.tokenType = res.tokenType
      this.email = res.email
      this.roles = res.roles
      this.persist()
    },

    async register(payload: RegisterRequest) {
      const res = await authApi.register(payload)
      this.token = res.token
      this.tokenType = res.tokenType
      this.email = res.email
      this.roles = res.roles
      this.persist()
    },

    logout() {
      this.token = null
      this.email = null
      this.roles = []
      localStorage.removeItem(STORAGE_KEY)
    },
  },
})
