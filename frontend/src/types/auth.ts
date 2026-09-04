import { i18n } from '@/i18n'
import { humanise } from './room'

export type Role =
  | 'GUEST'
  | 'COMPANY_CLIENT'
  | 'RECEPTIONIST'
  | 'HOTEL_MANAGER'
  | 'ADMIN'
  | 'SUPPORT'

export function roleLabel(role: string): string {
  const key = `role.${role}`
  const translated = i18n.global.t(key)
  return translated === key ? humanise(role) : translated
}

export interface AuthResponse {
  token: string
  /** Exchanged at /api/auth/refresh for a new access token once this one's short TTL
   *  (jwt.expiration, ~15 min) runs out — see api/http.ts's 401 interceptor. Rotates on
   *  every refresh: the value here is only ever the *current* one, never reused. */
  refreshToken: string
  tokenType: string
  userId: number
  email: string
  roles: Role[]
}

export interface OtpVerifyResponse {
  newAccount: boolean
  /** Present when `newAccount` is true — pass to /complete-registration. */
  verificationTicket?: string
  /** Present when `newAccount` is false — the caller is already logged in. */
  auth?: AuthResponse
}

export interface LoginRequest {
  identifier: string
  password: string
}

export interface CompleteRegistrationRequest {
  verificationTicket: string
  firstName: string
  lastName: string
  /** Date only: `YYYY-MM-DD`. Server enforces a minimum age of 18. */
  dateOfBirth: string
  password: string
}

/** Mirrors the backend's `exception.ApiError` exactly. */
export interface ApiError {
  status: number
  message: string
  timestamp: string
  errors?: string[]
}
