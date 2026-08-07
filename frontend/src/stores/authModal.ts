import { defineStore } from 'pinia'

/**
 * Two steps for both flows now that a password is the first factor: `form` collects
 * credentials (sign in: email + password; register: name + email + DOB + password) and,
 * on success, sends the email code; `code` collects that code and finishes — logging in
 * directly for sign-in, or silently calling complete-registration with the details
 * already gathered in `form` for a new account.
 */
export type AuthStep = 'form' | 'code'

/** Which of the two very different `form` steps to render and which endpoint it submits to. */
export type AuthIntent = 'signin' | 'register'

interface AuthModalState {
  open: boolean
  step: AuthStep
  intent: AuthIntent
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
    step: 'form',
    intent: 'signin',
    identifier: '',
    verificationTicket: null,
    intendedRoute: null,
    resolver: null,
  }),

  actions: {
    /** Opens the modal and resolves to whether the user ended up signed in. */
    prompt(intendedRoute: string | null = null, intent: AuthIntent = 'signin'): Promise<boolean> {
      this.open = true
      this.step = 'form'
      this.intent = intent
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
      this.step = 'form'
      this.verificationTicket = null
    },
  },
})
