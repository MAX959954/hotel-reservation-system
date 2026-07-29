import { http } from './http'
import type { BookingRequest, BookingResponse, BookingStatus } from '../types/booking'

export const bookingsApi = {
  create(payload: BookingRequest) {
    return http.post<BookingResponse>('/api/bookings', payload).then((r) => r.data)
  },
  getById(id: number) {
    return http.get<BookingResponse>(`/api/bookings/${id}`).then((r) => r.data)
  },
  getByUser(userId: number) {
    return http.get<BookingResponse[]>(`/api/bookings/user/${userId}`).then((r) => r.data)
  },
  getByUserAndStatus(userId: number, status: BookingStatus) {
    return http
      .get<BookingResponse[]>(`/api/bookings/user/${userId}/status/${status}`)
      .then((r) => r.data)
  },
  confirm(id: number) {
    return http.patch<BookingResponse>(`/api/bookings/${id}/confirm`).then((r) => r.data)
  },
  cancel(id: number) {
    return http.patch<BookingResponse>(`/api/bookings/${id}/cancel`).then((r) => r.data)
  },
}
