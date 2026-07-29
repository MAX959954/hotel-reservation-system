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
  email: string
  roles: Role[]
}

export interface RegisterRequest {
  firstName: string
  lastName: string
  email: string
  password: string
  phone: string
}

export interface LogInRequest {
  email: string
  password: string
}
