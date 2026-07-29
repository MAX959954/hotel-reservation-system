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

export type RoomStatus =
  | 'AVAILABLE'
  | 'OCCUPIED'
  | 'RESERVED'
  | 'MAINTENANCE'
  | 'OUT_OF_ORDER'

export interface RoomResponse {
  id: number
  number: string
  type: RoomType
  pricePerNight: number
  capacity: number
  floor: number
  status: RoomStatus
  description: string
  createdAt: string
  hotelId: number
  hotelName: string
}
