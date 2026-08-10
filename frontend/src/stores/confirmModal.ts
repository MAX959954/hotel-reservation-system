import { defineStore } from 'pinia'

interface ConfirmOptions {
  title?: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  /** Styles the confirm button as destructive (rose) instead of the usual champagne. */
  danger?: boolean
}

interface ConfirmModalState extends Required<ConfirmOptions> {
  open: boolean
  resolver: ((confirmed: boolean) => void) | null
}

/**
 * Replaces window.confirm() with something that actually looks like the rest of Folio.
 * Same resolver pattern as authModal: `ask()` opens the dialog and returns a Promise that
 * settles once the user picks a button, so call sites just `await` it like a native confirm.
 */
export const useConfirmModalStore = defineStore('confirmModal', {
  state: (): ConfirmModalState => ({
    open: false,
    title: '',
    message: '',
    confirmLabel: 'Confirm',
    cancelLabel: 'Cancel',
    danger: false,
    resolver: null,
  }),

  actions: {
    ask(options: ConfirmOptions): Promise<boolean> {
      this.open = true
      this.title = options.title ?? ''
      this.message = options.message
      this.confirmLabel = options.confirmLabel ?? 'Confirm'
      this.cancelLabel = options.cancelLabel ?? 'Cancel'
      this.danger = options.danger ?? false
      return new Promise<boolean>((resolve) => {
        this.resolver = resolve
      })
    },

    resolve(confirmed: boolean) {
      this.open = false
      this.resolver?.(confirmed)
      this.resolver = null
    },
  },
})
