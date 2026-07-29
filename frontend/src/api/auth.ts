import { http } from './http'
import type { AuthResponse, LogInRequest, RegisterRequest } from '../types/auth'

export const authApi = {
  register(payload: RegisterRequest) {
    return http.post<AuthResponse>('/api/auth/register', payload).then((r) => r.data)
  },
  login(payload: LogInRequest) {
    return http.post<AuthResponse>('/api/auth/login', payload).then((r) => r.data)
  },
}
