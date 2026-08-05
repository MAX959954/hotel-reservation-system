export interface ReviewResponse {
  id: number
  bookingId: number
  userId: number
  userFullName: string
  roomId: number
  roomNumber: string
  hotelName: string
  rating: number
  comment: string
  approved: boolean
  createdAt: string
  stayCheckIn: string
  stayCheckOut: string
}
