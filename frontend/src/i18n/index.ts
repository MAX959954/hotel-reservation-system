import { createI18n } from 'vue-i18n'
import en from '@/locales/en'
import sk from '@/locales/sk'
import ru from '@/locales/ru'
import es from '@/locales/es'
import fr from '@/locales/fr'
import it from '@/locales/it'
import ja from '@/locales/ja'

export const SUPPORTED_LOCALES = [
  { code: 'en', nativeName: 'English' },
  { code: 'sk', nativeName: 'Slovenčina' },
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

// Slovak and Russian have three plural forms ("1 noc / 2 noci / 5 nocí"), which
// vue-i18n's default two-form rule can't express. The other locales use the default.
function slovakPlural(choice: number): number {
  if (choice === 1) return 0
  return choice >= 2 && choice <= 4 ? 1 : 2
}

function russianPlural(choice: number): number {
  const mod10 = choice % 10
  const mod100 = choice % 100
  if (mod10 === 1 && mod100 !== 11) return 0
  return mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? 1 : 2
}

export const i18n = createI18n({
  legacy: false,
  locale: loadLocale(),
  fallbackLocale: 'en',
  messages: { en, sk, ru, es, fr, it, ja },
  pluralRules: { sk: slovakPlural, ru: russianPlural },
})

export function setLocale(code: LocaleCode) {
  i18n.global.locale.value = code
  localStorage.setItem(STORAGE_KEY, code)
  document.documentElement.lang = code
}

document.documentElement.lang = i18n.global.locale.value
