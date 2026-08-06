import { http } from './http'
import type { AuthResponse, CompleteRegistrationRequest, OtpVerifyResponse } from '@/types/auth'

export const authApi = {
  /** Server validates the identifier with `@Email` — email only, a phone number 400s. */
  async requestOtp(identifier: string): Promise<void> {
    await http.post('/api/auth/otp/request', { identifier })
  },

  async verifyOtp(identifier: string, code: string): Promise<OtpVerifyResponse> {
    const { data } = await http.post<OtpVerifyResponse>('/api/auth/otp/verify', { identifier, code })
    return data
  },

  async completeRegistration(request: CompleteRegistrationRequest): Promise<AuthResponse> {
    const { data } = await http.post<AuthResponse>('/api/auth/complete-registration', request)
    return data
  },

  async google(idToken: string): Promise<AuthResponse> {
    const { data } = await http.post<AuthResponse>('/api/auth/google', { idToken })
    return data
  },
}
