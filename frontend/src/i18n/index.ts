import { createI18n } from 'vue-i18n'
import en from './locales/en'
import es from './locales/es'
import fr from './locales/fr'
import de from './locales/de'
import pt from './locales/pt'

export const SUPPORTED_LOCALES = [
  { code: 'en', label: 'English' },
  { code: 'es', label: 'Español' },
  { code: 'fr', label: 'Français' },
  { code: 'de', label: 'Deutsch' },
  { code: 'pt', label: 'Português' },
] as const

export type LocaleCode = (typeof SUPPORTED_LOCALES)[number]['code']

const STORAGE_KEY = 'hotel-locale'

function resolveInitialLocale(): LocaleCode {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored && SUPPORTED_LOCALES.some((l) => l.code === stored)) return stored as LocaleCode

  const browser = navigator.language?.slice(0, 2)
  if (SUPPORTED_LOCALES.some((l) => l.code === browser)) return browser as LocaleCode

  return 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: resolveInitialLocale(),
  fallbackLocale: 'en',
  messages: { en, es, fr, de, pt },
})

export function setLocale(code: LocaleCode) {
  i18n.global.locale.value = code
  localStorage.setItem(STORAGE_KEY, code)
  document.documentElement.lang = code
}
