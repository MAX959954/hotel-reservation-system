import { defineStore } from 'pinia'

export type AuthModalStep = 'identify' | 'code' | 'register'

interface AuthModalState {
  isOpen: boolean
  step: AuthModalStep
  identifier: string
  verificationTicket: string | null
  redirectTo: string | null
}

export const useAuthModalStore = defineStore('authModal', {
  state: (): AuthModalState => ({
    isOpen: false,
    step: 'identify',
    identifier: '',
    verificationTicket: null,
    redirectTo: null,
  }),

  actions: {
    open(redirectTo?: string) {
      this.isOpen = true
      this.step = 'identify'
      this.identifier = ''
      this.verificationTicket = null
      this.redirectTo = redirectTo ?? null
    },

    close() {
      this.isOpen = false
    },

    goToCode(identifier: string) {
      this.identifier = identifier
      this.step = 'code'
    },

    goToRegister(verificationTicket: string) {
      this.verificationTicket = verificationTicket
      this.step = 'register'
    },

    backToIdentify() {
      this.step = 'identify'
      this.verificationTicket = null
    },
  },
})
