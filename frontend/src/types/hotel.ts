export type HotelStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'UNDER_RENOVATION'
  | 'COMINGS_SOON'
  | 'CLOSED'
  | 'SUSPENDED'

export interface HotelResponse {
  id: number
  name: string
  city: string
  country: string
  address: string
  startRating: number
  phone: string
  email: string
  description: string
  imageUrl: string
  status: HotelStatus
  companyId: number
  companyName: string
}
