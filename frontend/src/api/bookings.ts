import { http } from './http'
import type { BookingRequest, BookingResponse } from '@/types/booking'

export const bookingsApi = {
  async create(request: BookingRequest): Promise<BookingResponse> {
    const { data } = await http.post<BookingResponse>('/api/bookings', request)
    return data
  },

  async getByUser(userId: number): Promise<BookingResponse[]> {
    const { data } = await http.get<BookingResponse[]>(`/api/bookings/user/${userId}`)
    return data
  },

  async cancel(id: number): Promise<BookingResponse> {
    const { data } = await http.patch<BookingResponse>(`/api/bookings/${id}/cancel`)
    return data
  },
}
