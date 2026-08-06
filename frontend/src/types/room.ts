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

const ROOM_TYPE_LABELS: Record<RoomType, string> = {
  SINGLE: 'Single',
  DOUBLE: 'Double',
  TWIN: 'Twin',
  TRIPLE: 'Triple',
  SUITE: 'Suite',
  JUNIOR_SUITE: 'Junior suite',
  DELUXE: 'Deluxe',
  PENTHOUSE: 'Penthouse',
  FAMILY: 'Family',
  CONNECTING: 'Connecting',
  DORMITORY: 'Dormitory',
  STUDIO: 'Studio',
  VILLA: 'Villa',
  BUNGALOW: 'Bungalow',
  ACCESSIBLE: 'Accessible',
}

export function roomTypeLabel(type: RoomType): string {
  return ROOM_TYPE_LABELS[type] ?? humanise(type)
}

/** Fallback for any value the API adds before this map is updated. */
export function humanise(value: string): string {
  const lower = value.replace(/_/g, ' ').toLowerCase()
  return lower.charAt(0).toUpperCase() + lower.slice(1)
}
