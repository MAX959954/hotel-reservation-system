import { i18n } from '@/i18n'

/** Hotel-standard clock times, so a date-only picker still produces a valid LocalDateTime. */
export const CHECK_IN_TIME = '15:00:00'
export const CHECK_OUT_TIME = '11:00:00'

/**
 * The backend parses `LocalDateTime`, so the string must carry no timezone suffix.
 * `new Date().toISOString()` would append `Z` and shift the day across timezones —
 * hence the manual assembly.
 */
export function toLocalDateTime(date: string, time: string): string {
  return `${date}T${time}`
}

/**
 * Nights are a calendar-date span, matching BookingServiceImpl: a Mon 15:00 →
 * Thu 11:00 stay is 3 nights. Counting elapsed hours would floor that to 2 and put
 * the price on screen out of step with the price the server charges.
 */
export function nightsBetween(checkInDate: string, checkOutDate: string): number {
  if (!checkInDate || !checkOutDate) return 0
  const start = Date.parse(`${checkInDate}T00:00:00`)
  const end = Date.parse(`${checkOutDate}T00:00:00`)
  if (Number.isNaN(start) || Number.isNaN(end)) return 0
  return Math.max(0, Math.round((end - start) / 86_400_000))
}

export function todayIso(): string {
  return new Date().toISOString().slice(0, 10)
}

export function addDaysIso(iso: string, days: number): string {
  const d = new Date(`${iso}T00:00:00`)
  d.setDate(d.getDate() + days)
  return d.toISOString().slice(0, 10)
}

export function formatDateRange(checkIn: string, checkOut: string, locale = i18n.global.locale.value): string {
  const fmt = new Intl.DateTimeFormat(locale, { day: 'numeric', month: 'short', year: 'numeric' })
  const a = new Date(checkIn)
  const b = new Date(checkOut)
  if (Number.isNaN(a.getTime()) || Number.isNaN(b.getTime())) return '—'
  return `${fmt.format(a)} → ${fmt.format(b)}`
}
