import { http } from './http'
import type { CreateRoomRequest, RoomResponse } from '@/types/room'

export const roomsApi = {
  async getByHotel(hotelId: number | string): Promise<RoomResponse[]> {
    const { data } = await http.get<RoomResponse[]>(`/api/rooms/hotels/${hotelId}`)
    return data
  },

  async create(request: CreateRoomRequest): Promise<RoomResponse> {
    const { data } = await http.post<RoomResponse>('/api/rooms', request)
    return data
  },

  async update(id: number, request: CreateRoomRequest): Promise<RoomResponse> {
    const { data } = await http.patch<RoomResponse>(`/api/rooms/${id}`, request)
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
