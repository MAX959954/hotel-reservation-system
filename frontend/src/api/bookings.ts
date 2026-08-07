import { http } from './http'
import type { BookingRequest, BookingResponse, BookingStatus } from '@/types/booking'

export const bookingsApi = {
  async create(request: BookingRequest): Promise<BookingResponse> {
    const { data } = await http.post<BookingResponse>('/api/bookings', request)
    return data
  },

  async getByUser(userId: number): Promise<BookingResponse[]> {
    const { data } = await http.get<BookingResponse[]>(`/api/bookings/user/${userId}`)
    return data
  },

  /** Every booking across a company's hotels — the owner/manager panel's data source. */
  async getByCompany(companyId: number, status?: BookingStatus): Promise<BookingResponse[]> {
    const { data } = await http.get<BookingResponse[]>(`/api/bookings/company/${companyId}`, {
      params: status ? { status } : undefined,
    })
    return data
  },

  async confirm(id: number): Promise<BookingResponse> {
    const { data } = await http.patch<BookingResponse>(`/api/bookings/${id}/confirm`)
    return data
  },

  async checkIn(id: number): Promise<BookingResponse> {
    const { data } = await http.patch<BookingResponse>(`/api/bookings/${id}/checkIn`)
    return data
  },

  async complete(id: number): Promise<BookingResponse> {
    const { data } = await http.patch<BookingResponse>(`/api/bookings/${id}/complete`)
    return data
  },

  async cancel(id: number): Promise<BookingResponse> {
    const { data } = await http.patch<BookingResponse>(`/api/bookings/${id}/cancel`)
    return data
  },
}
