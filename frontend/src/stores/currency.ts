import { defineStore } from 'pinia'

/**
 * The API returns prices as bare numbers with no currency field, so there is nothing
 * to convert *from*. This store picks one display currency and formats with it —
 * it deliberately does not pretend to do FX conversion, which would be inventing data.
 */
export const useCurrencyStore = defineStore('currency', {
  state: () => ({
    code: 'EUR' as const,
    locale: 'en-GB',
  }),

  getters: {
    format(state) {
      const formatter = new Intl.NumberFormat(state.locale, {
        style: 'currency',
        currency: state.code,
        maximumFractionDigits: 0,
      })
      return (value: number | null | undefined) =>
        value == null ? '—' : formatter.format(value)
    },
  },
})
