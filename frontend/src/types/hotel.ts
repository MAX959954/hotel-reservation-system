export type HotelStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'UNDER_RENOVATION'
  | 'COMINGS_SOON'
  | 'CLOSED'
  | 'SUSPENDED'

export type Amenity =
  | 'WIFI'
  | 'BREAKFAST'
  | 'AIR_CONDITIONING'
  | 'PARKING'
  | 'POOL'
  | 'GYM'
  | 'SPA'
  | 'BAR'
  | 'RESTAURANT'
  | 'ROOM_SERVICE'
  | 'AIRPORT_SHUTTLE'
  | 'PET_FRIENDLY'
  | 'ELEVATOR'
  | 'LAUNDRY'
  | 'WORKSPACE'
  | 'TV'
  | 'COFFEE_MAKER'
  | 'HAIR_DRYER'
  | 'LUGGAGE_STORAGE'
  | 'ACCESSIBLE'
  | 'EV_CHARGING'
  | 'NON_SMOKING'

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
  amenities: Amenity[]
}
