import { http } from './http'
import type { AuthResponse, CompleteRegistrationRequest, LoginRequest, OtpVerifyResponse } from '@/types/auth'

export const authApi = {
  /**
   * First factor of sign-in. Server validates the password and, if it matches, sends the
   * email code and responds 202 — the same shape as requestOtp below, but only reachable
   * for an account whose password just checked out.
   */
  async login(identifier: string, password: string): Promise<void> {
    const request: LoginRequest = { identifier, password }
    await http.post('/api/auth/login', request)
  },

  /**
   * Registration-only: the server refuses this for an email that already has an account
   * ("sign in with your password instead"), so it can no longer be used as a passwordless
   * login. Server also validates the identifier with `@Email` — email only.
   */
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
