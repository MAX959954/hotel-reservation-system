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
  verificationTicket?: string
  auth?: AuthResponse
}

export interface CompleteRegistrationRequest {
  verificationTicket: string
  firstName: string
  lastName: string
  dateOfBirth: string
  password: string
}
