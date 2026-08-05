export type BookingStatus =
  | 'PENDING'
  | 'CONFIRMED'
  | 'CHECKED_IN'
  | 'COMPLETED'
  | 'CANCELLED'
  | 'NO_SHOW'
  | 'PAYMENT_FAILED'

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

export interface BookingRequest {
  roomId: number
  checkIn: string
  checkOut: string
  guestCount: number
  specialRequest?: string
}
