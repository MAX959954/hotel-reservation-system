import axios from 'axios'
import { http } from './http'
import type { PaymentRequest, PaymentResponse } from '@/types/payment'

export const paymentsApi = {
  async pay(request: PaymentRequest): Promise<PaymentResponse> {
    const { data } = await http.post<PaymentResponse>('/api/payments', request)
    return data
  },

  /** null means "no payment recorded yet" — the server 400s that case rather than 404ing. */
  async getByBooking(bookingId: number): Promise<PaymentResponse | null> {
    try {
      const { data } = await http.get<PaymentResponse>(`/api/payments/booking/${bookingId}`)
      return data
    } catch (e) {
      if (axios.isAxiosError(e) && e.response?.status === 400) return null
      throw e
    }
  },
}
