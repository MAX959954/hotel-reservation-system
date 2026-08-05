import { http } from './http'
import type { AuthResponse, CompleteRegistrationRequest, OtpVerifyResponse } from '../types/auth'

export const authApi = {
  requestOtp(identifier: string) {
    return http.post<void>('/api/auth/otp/request', { identifier }).then((r) => r.data)
  },
  verifyOtp(identifier: string, code: string) {
    return http.post<OtpVerifyResponse>('/api/auth/otp/verify', { identifier, code }).then((r) => r.data)
  },
  completeRegistration(payload: CompleteRegistrationRequest) {
    return http.post<AuthResponse>('/api/auth/complete-registration', payload).then((r) => r.data)
  },
  google(idToken: string) {
    return http.post<AuthResponse>('/api/auth/google', { idToken }).then((r) => r.data)
  },
}
