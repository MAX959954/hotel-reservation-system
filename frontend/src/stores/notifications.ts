import { defineStore } from 'pinia'
import { bookingsApi } from '@/api/bookings'
import { useAuthStore } from './auth'

const STORAGE_KEY = 'folio-notifications-seen'

/**
 * There's no notifications table in the backend (see NotificationsView's own comment) —
 * the feed there is derived from real booking timestamps (createdAt/confirmedAt/
 * cancelledAt). The "unread" badge follows the same rule: honest, not invented. A booking
 * event counts as unread if its timestamp is after the last time the user opened
 * /notifications, tracked as a single ISO string in localStorage.
 */
export const useNotificationsStore = defineStore('notifications', {
  state: () => ({
    timestamps: [] as string[],
    lastSeenAt: localStorage.getItem(STORAGE_KEY),
    loaded: false,
  }),

  getters: {
    unreadCount(state): number {
      const cutoff = state.lastSeenAt ? Date.parse(state.lastSeenAt) : 0
      return state.timestamps.filter((t) => Date.parse(t) > cutoff).length
    },
  },

  actions: {
    async load() {
      if (this.loaded) return
      const auth = useAuthStore()
      if (!auth.userId) return
      this.loaded = true
      try {
        const bookings = await bookingsApi.getByUser(auth.userId)
        const timestamps: string[] = []
        for (const b of bookings) {
          timestamps.push(b.createdAt)
          if (b.confirmedAt) timestamps.push(b.confirmedAt)
          if (b.cancelledAt) timestamps.push(b.cancelledAt)
        }
        this.timestamps = timestamps
      } catch {
        this.loaded = false
      }
    },

    /** Called when /notifications is opened — everything up to now is "read". */
    markSeen() {
      const now = new Date().toISOString()
      this.lastSeenAt = now
      localStorage.setItem(STORAGE_KEY, now)
    },

    reset() {
      this.timestamps = []
      this.loaded = false
    },
  },
})
