import { defineStore } from 'pinia'
import { hotelsApi } from '@/api/hotels'
import type { HotelResponse } from '@/types/hotel'

/**
 * Prefetch cache for hotel detail. The flythrough warms this as each card becomes
 * active, so opening a stay renders immediately instead of showing a skeleton for a
 * request that could have been made seconds earlier.
 *
 * Rooms are deliberately not prefetched: that call needs checkIn/checkOut, which the
 * flythrough does not know, and inventing dates would show availability for a stay the
 * user never asked about.
 */
export const useHotelsStore = defineStore('hotels', {
  state: () => ({
    cache: {} as Record<number, HotelResponse>,
    inFlight: new Set<number>(),
  }),

  getters: {
    cached: (state) => (id: number): HotelResponse | undefined => state.cache[id],
  },

  actions: {
    /** Best-effort — a failed prefetch must never surface as an error to the user. */
    async prefetch(id: number) {
      if (this.cache[id] || this.inFlight.has(id)) return
      this.inFlight.add(id)
      try {
        this.cache[id] = await hotelsApi.getById(id)
      } catch {
        // Swallowed on purpose: the detail view will retry and report properly.
      } finally {
        this.inFlight.delete(id)
      }
    },

    put(hotel: HotelResponse) {
      this.cache[hotel.id] = hotel
    },
  },
})
