import { defineStore } from 'pinia'

export const SUPPORTED_CURRENCIES = [
  { code: 'USD', symbol: '$', label: 'US Dollar', rateFromUsd: 1 },
  { code: 'EUR', symbol: '€', label: 'Euro', rateFromUsd: 0.92 },
  { code: 'GBP', symbol: '£', label: 'British Pound', rateFromUsd: 0.79 },
  { code: 'JPY', symbol: '¥', label: 'Japanese Yen', rateFromUsd: 149.5 },
] as const

export type CurrencyCode = (typeof SUPPORTED_CURRENCIES)[number]['code']

const STORAGE_KEY = 'hotel-currency'

function loadInitial(): CurrencyCode {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored && SUPPORTED_CURRENCIES.some((c) => c.code === stored)) return stored as CurrencyCode
  return 'USD'
}

export const useCurrencyStore = defineStore('currency', {
  state: () => ({
    code: loadInitial() as CurrencyCode,
  }),

  getters: {
    current: (state) => SUPPORTED_CURRENCIES.find((c) => c.code === state.code)!,
  },

  actions: {
    setCurrency(code: CurrencyCode) {
      this.code = code
      localStorage.setItem(STORAGE_KEY, code)
    },

    // Room prices from the API are in USD. Rates are fixed approximations for
    // display only — not sourced from a live feed, not used for real settlement.
    format(amountUsd: number): string {
      const { symbol, rateFromUsd } = this.current
      const converted = amountUsd * rateFromUsd
      return `${symbol}${converted.toFixed(2)}`
    },
  },
})
