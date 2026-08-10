import { defineStore } from 'pinia'
import { i18n } from '@/i18n'

export type CurrencyCode = 'EUR' | 'USD' | 'GBP' | 'JPY'

export interface CurrencyInfo {
  code: CurrencyCode
  label: string
  locale: string
}

export const CURRENCIES: CurrencyInfo[] = [
  { code: 'EUR', label: 'Euro', locale: 'en-GB' },
  { code: 'USD', label: 'US Dollar', locale: 'en-US' },
  { code: 'GBP', label: 'British Pound', locale: 'en-GB' },
  { code: 'JPY', label: 'Japanese Yen', locale: 'ja-JP' },
]

const BASE_CURRENCY: CurrencyCode = 'EUR'

/**
 * Fixed, clearly-illustrative rates — not a live feed, and never used to change the
 * authoritative price. The API returns bare numbers with no currency field; every price
 * in this app *is* that number in EUR. PRODUCT.md is explicit that implying a real,
 * live conversion would be inventing data the backend doesn't provide.
 *
 * What this table drives instead is a secondary, clearly-labelled estimate — the same
 * pattern Booking.com and similar sites use for "≈ $162" next to the real charged price.
 * The EUR figure stays primary and unambiguous; the converted figure is always annotated
 * as an estimate, never presented as what the guest will actually be charged.
 */
const ILLUSTRATIVE_RATE_FROM_EUR: Record<CurrencyCode, number> = {
  EUR: 1,
  USD: 1.08,
  GBP: 0.86,
  JPY: 162,
}

const STORAGE_KEY = 'folio-currency'

function loadCode(): CurrencyCode {
  const saved = localStorage.getItem(STORAGE_KEY)
  return CURRENCIES.some((c) => c.code === saved) ? (saved as CurrencyCode) : BASE_CURRENCY
}

export const useCurrencyStore = defineStore('currency', {
  state: () => ({
    code: loadCode(),
  }),

  getters: {
    info(state): CurrencyInfo {
      return CURRENCIES.find((c) => c.code === state.code) ?? CURRENCIES[0]
    },

    isEstimate(state): boolean {
      return state.code !== BASE_CURRENCY
    },

    /** The real, charged price. Always EUR, regardless of the display currency chosen. */
    format() {
      const base = CURRENCIES.find((c) => c.code === BASE_CURRENCY)!
      const formatter = new Intl.NumberFormat(base.locale, {
        style: 'currency',
        currency: BASE_CURRENCY,
        maximumFractionDigits: 0,
      })
      return (value: number | null | undefined) => (value == null ? '—' : formatter.format(value))
    },

    /**
     * A secondary "≈ $162 estimated" string using the fixed illustrative rate, or null
     * when the display currency already is the base currency (nothing to estimate).
     */
    estimate(): (value: number | null | undefined) => string | null {
      const { code, locale } = this.info
      if (code === BASE_CURRENCY) return () => null
      const formatter = new Intl.NumberFormat(locale, {
        style: 'currency',
        currency: code,
        maximumFractionDigits: 0,
      })
      return (value: number | null | undefined) =>
        value == null
          ? null
          : i18n.global.t('currency.estimated', { amount: formatter.format(value * ILLUSTRATIVE_RATE_FROM_EUR[code]) })
    },
  },

  actions: {
    setCode(code: CurrencyCode) {
      this.code = code
      localStorage.setItem(STORAGE_KEY, code)
    },
  },
})
