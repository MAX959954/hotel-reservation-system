import { http } from './http'
import type { RoomResponse } from '@/types/room'

export const roomsApi = {
  async getByHotel(hotelId: number | string): Promise<RoomResponse[]> {
    const { data } = await http.get<RoomResponse[]>(`/api/rooms/hotels/${hotelId}`)
    return data
  },

  async getAvailable(
    hotelId: number | string,
    checkIn: string,
    checkOut: string,
    guestCount?: number,
  ): Promise<RoomResponse[]> {
    const { data } = await http.get<RoomResponse[]>(`/api/rooms/hotels/${hotelId}/available`, {
      params: { checkIn, checkOut, ...(guestCount ? { guestCount } : {}) },
    })
    return data
  },
}
