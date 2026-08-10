import { i18n } from '@/i18n'

export type RoomType =
  | 'SINGLE'
  | 'DOUBLE'
  | 'TWIN'
  | 'TRIPLE'
  | 'SUITE'
  | 'JUNIOR_SUITE'
  | 'DELUXE'
  | 'PENTHOUSE'
  | 'FAMILY'
  | 'CONNECTING'
  | 'DORMITORY'
  | 'STUDIO'
  | 'VILLA'
  | 'BUNGALOW'
  | 'ACCESSIBLE'

export type RoomStatus = 'AVAILABLE' | 'OCCUPIED' | 'RESERVED' | 'MAINTENANCE' | 'OUT_OF_ORDER'

export interface RoomResponse {
  id: number
  number: string
  type: RoomType
  pricePerNight: number
  capacity: number
  floor: number
  status: RoomStatus
  description: string | null
  createdAt: string
  hotelId: number
  hotelName: string
}

/** A fixed, closed enum — every value has a translation key (see locales/en.ts's
 *  `roomType`), so this never needs the raw-string fallback in practice. The fallback
 *  stays as protection against the backend adding a value before the locale files catch up. */
export function roomTypeLabel(type: RoomType): string {
  const key = `roomType.${type}`
  const translated = i18n.global.t(key)
  return translated === key ? humanise(type) : translated
}

/** Fallback for any value the API adds before this map is updated. */
export function humanise(value: string): string {
  const lower = value.replace(/_/g, ' ').toLowerCase()
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}
