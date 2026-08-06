import { defineStore } from 'pinia'
import type { AuthResponse, Role } from '@/types/auth'

const STORAGE_KEY = 'folio-auth'

interface AuthState {
  token: string | null
  tokenType: string
  userId: number | null
  email: string | null
  roles: Role[]
}

function emptyState(): AuthState {
  return { token: null, tokenType: 'Bearer', userId: null, email: null, roles: [] }
}

function loadState(): AuthState {
  const raw = localStorage.getItem(STORAGE_KEY)
  if (!raw) return emptyState()
  try {
    return { ...emptyState(), ...(JSON.parse(raw) as Partial<AuthState>) }
  } catch {
    return emptyState()
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
          userId: this.userId,
          email: this.email,
          roles: this.roles,
        }),
      )
    },

    setSession(res: AuthResponse) {
      this.token = res.token
      this.tokenType = res.tokenType || 'Bearer'
      this.userId = res.userId
      this.email = res.email
      this.roles = res.roles ?? []
      this.persist()
    },

    logout() {
      this.$patch(emptyState())
      localStorage.removeItem(STORAGE_KEY)
    },
  },
})
