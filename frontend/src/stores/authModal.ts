import { defineStore } from 'pinia'

export type AuthStep = 'identifier' | 'code' | 'register'

interface AuthModalState {
  open: boolean
  step: AuthStep
  identifier: string
  verificationTicket: string | null
  /** Where to continue once sign-in succeeds — set by the router guard. */
  intendedRoute: string | null
  /** Resolved once the user is authenticated, so callers can await a sign-in. */
  resolver: ((success: boolean) => void) | null
}

export const useAuthModalStore = defineStore('authModal', {
  state: (): AuthModalState => ({
    open: false,
    step: 'identifier',
    identifier: '',
    verificationTicket: null,
    intendedRoute: null,
    resolver: null,
  }),

  actions: {
    /** Opens the modal and resolves to whether the user ended up signed in. */
    prompt(intendedRoute: string | null = null): Promise<boolean> {
      this.open = true
      this.step = 'identifier'
      this.identifier = ''
      this.verificationTicket = null
      this.intendedRoute = intendedRoute
      return new Promise<boolean>((resolve) => {
        this.resolver = resolve
      })
    },

    close(success = false) {
      this.open = false
      this.resolver?.(success)
      this.resolver = null
      this.step = 'identifier'
      this.verificationTicket = null
    },
  },
})
