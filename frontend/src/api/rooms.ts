import { http } from './http'
import type { RoomResponse, RoomStatus, RoomType } from '../types/room'

export const roomsApi = {
  getById(id: number) {
    return http.get<RoomResponse>(`/api/rooms/${id}`).then((r) => r.data)
  },
  getByHotel(hotelId: number) {
    return http.get<RoomResponse[]>(`/api/rooms/hotels/${hotelId}`).then((r) => r.data)
  },
  getByType(type: RoomType) {
    return http.get<RoomResponse[]>(`/api/rooms/type/${type}`).then((r) => r.data)
  },
  getByStatus(status: RoomStatus) {
    return http.get<RoomResponse[]>(`/api/rooms/status/${status}`).then((r) => r.data)
  },
  getByHotelAndStatus(hotelId: number, status: RoomStatus) {
    return http
      .get<RoomResponse[]>(`/api/rooms/hotels/${hotelId}/status/${status}`)
      .then((r) => r.data)
  },
  getByHotelAndCapacity(hotelId: number, guestCount: number) {
    return http
      .get<RoomResponse[]>(`/api/rooms/hotels/${hotelId}/capacity/${guestCount}`)
      .then((r) => r.data)
  },
  getAvailableForHotel(hotelId: number) {
    return http.get<RoomResponse[]>(`/api/rooms/hotels/${hotelId}/available`).then((r) => r.data)
  },
}
