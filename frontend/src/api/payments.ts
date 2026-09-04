import axios from 'axios'
import { http } from './http'
import type { PaymentIntentResponse, PaymentRequest, PaymentResponse } from '@/types/payment'

export const paymentsApi = {
  async pay(request: PaymentRequest): Promise<PaymentResponse> {
    const { data } = await http.post<PaymentResponse>('/api/payments', request)
    return data
  },

  /** Card / Google Pay only — see PaymentServiceImpl.GATEWAY_METHODS on the backend.
   *  Sends a fresh Idempotency-Key per call: if a dropped connection makes the browser
   *  resend this exact request below the JS layer (invisible to any "submitting" flag
   *  here), the backend forwards the same key to Stripe and gets back the original
   *  PaymentIntent instead of creating — and charging — a second one. */
  async createIntent(request: PaymentRequest): Promise<PaymentIntentResponse> {
    const { data } = await http.post<PaymentIntentResponse>('/api/payments/intent', request, {
      headers: { 'Idempotency-Key': crypto.randomUUID() },
    })
    return data
  },

  /** Call once Stripe.js reports the PaymentIntent succeeded — the backend re-verifies
   *  with Stripe itself before marking the booking paid, this call doesn't just "tell" it to. */
  async confirm(paymentId: number): Promise<PaymentResponse> {
    const { data } = await http.post<PaymentResponse>(`/api/payments/${paymentId}/confirm`)
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
