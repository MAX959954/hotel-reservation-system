import { i18n } from '@/i18n'
import { humanise } from './room'

export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'PAYMENT_FAILED'

export function bookingStatusLabel(status: string): string {
  const key = `bookingStatus.${status}`
  const translated = i18n.global.t(key)
  return translated === key ? humanise(status) : translated
}

/**
 * Deliberately has no `userId`: the server takes the booking's owner from the JWT.
 * Sending one from the client is ignored by design — see BookingServiceImpl.create.
 */
export interface BookingRequest {
  roomId: number
  /** `YYYY-MM-DDTHH:mm:ss` — LocalDateTime, no timezone suffix. */
  checkIn: string
  checkOut: string
  guestCount: number
  specialRequest?: string
}

export interface BookingResponse {
  id: number
  userId: number
  userFullName: string
  roomId: number
  roomNumber: string
  hotelId: number
  hotelName: string
  checkIn: string
  checkOut: string
  guestCount: number
  bookingStatus: BookingStatus
  totalPrice: number
  specialRequest: string | null
  confirmedAt: string | null
  cancelledAt: string | null
  createdAt: string
}
