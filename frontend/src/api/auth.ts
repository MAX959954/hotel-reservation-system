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

  /** Exchanges a still-valid refresh token for a new access token (rotated: the one
   *  passed in is consumed and can't be reused). Called by http.ts's 401 interceptor,
   *  not normally from a component directly. */
  async refresh(refreshToken: string): Promise<AuthResponse> {
    const { data } = await http.post<AuthResponse>('/api/auth/refresh', { refreshToken })
    return data
  },

  /** Best-effort server-side revocation — still safe to call with a refresh token that's
   *  already expired, or with no access token in the request at all (this app doesn't
   *  pass one explicitly; the request interceptor already attaches whatever's current). */
  async logout(refreshToken: string): Promise<void> {
    await http.post('/api/auth/logout', { refreshToken })
  },
}
