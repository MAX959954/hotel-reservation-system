import { createI18n } from 'vue-i18n'
import en from '@/locales/en'
import ru from '@/locales/ru'
import es from '@/locales/es'
import fr from '@/locales/fr'
import it from '@/locales/it'
import ja from '@/locales/ja'

export const SUPPORTED_LOCALES = [
  { code: 'en', nativeName: 'English' },
  { code: 'ru', nativeName: 'Русский' },
  { code: 'es', nativeName: 'Español' },
  { code: 'fr', nativeName: 'Français' },
  { code: 'it', nativeName: 'Italiano' },
  { code: 'ja', nativeName: '日本語' },
] as const

export type LocaleCode = (typeof SUPPORTED_LOCALES)[number]['code']

const STORAGE_KEY = 'folio-locale'

function loadLocale(): LocaleCode {
  const saved = localStorage.getItem(STORAGE_KEY)
  if (SUPPORTED_LOCALES.some((l) => l.code === saved)) return saved as LocaleCode
  // Best-effort: match the browser's language before falling back to English, so a
  // first-time Spanish-browser visitor doesn't have to find the switcher themselves.
  const browserLang = navigator.language.slice(0, 2)
  return SUPPORTED_LOCALES.some((l) => l.code === browserLang) ? (browserLang as LocaleCode) : 'en'
}

export const i18n = createI18n({
  legacy: false,
  locale: loadLocale(),
  fallbackLocale: 'en',
  messages: { en, ru, es, fr, it, ja },
})

export function setLocale(code: LocaleCode) {
  i18n.global.locale.value = code
  localStorage.setItem(STORAGE_KEY, code)
  document.documentElement.lang = code
}

document.documentElement.lang = i18n.global.locale.value
