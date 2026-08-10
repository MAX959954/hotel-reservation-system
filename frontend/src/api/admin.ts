import { http } from './http'
import type { UserProfileResponse } from '@/types/account'
import type { Role } from '@/types/auth'

export const adminApi = {
  async grantRole(userId: number, role: Role): Promise<UserProfileResponse> {
    const { data } = await http.post<UserProfileResponse>(`/api/admin/users/${userId}/roles/${role}`)
    return data
  },

  async revokeRole(userId: number, role: Role): Promise<UserProfileResponse> {
    const { data } = await http.delete<UserProfileResponse>(`/api/admin/users/${userId}/roles/${role}`)
    return data
  },
}
