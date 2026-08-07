import { defineStore } from 'pinia'

export type SettingsTab = 'personal' | 'security' | 'currency'

interface SettingsModalState {
  open: boolean
  tab: SettingsTab
}

/**
 * Shared trigger for the settings surface: the footer's currency pill and the account
 * menu's "Region & currency" / "Account settings" items all open the same modal, just
 * landed on a different tab, rather than each owning a separate copy of the same form.
 */
export const useSettingsModalStore = defineStore('settingsModal', {
  state: (): SettingsModalState => ({
    open: false,
    tab: 'personal',
  }),

  actions: {
    openTab(tab: SettingsTab = 'personal') {
      this.tab = tab
      this.open = true
    },
    close() {
      this.open = false
    },
  },
})
