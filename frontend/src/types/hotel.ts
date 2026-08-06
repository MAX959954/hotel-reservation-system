export type HotelStatus =
  | 'ACTIVE'
  | 'INACTIVE'
  | 'UNDER_RENOVATION'
  | 'COMING_SOON'
  | 'CLOSED'
  | 'SUSPENDED'

/** Closed enum on the backend (hotels.Amenity) — 22 values, nothing else can arrive. */
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
  /** Yes, `startRating` — that is the real API spelling, not a typo on our side. */
  startRating: number
  phone: string | null
  email: string | null
  description: string | null
  imageUrl: string | null
  status: HotelStatus
  companyId: number
  companyName: string
  amenities: Amenity[] | null
}
