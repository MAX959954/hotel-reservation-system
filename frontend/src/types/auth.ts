export type Role =
  | 'GUEST'
  | 'COMPANY_CLIENT'
  | 'RECEPTIONIST'
  | 'HOTEL_MANAGER'
  | 'ADMIN'
  | 'SUPPORT'

export interface AuthResponse {
  token: string
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
