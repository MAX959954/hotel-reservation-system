import { http } from './http'
import type { AccountStatus, UserProfileResponse } from '@/types/account'
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

  /** The users board's data source — every argument left undefined is not filtered on. */
  async searchUsers(params: { search?: string; role?: Role; status?: AccountStatus }): Promise<UserProfileResponse[]> {
    const { data } = await http.get<UserProfileResponse[]>('/api/admin/users', { params })
    return data
  },

  async getUser(userId: number): Promise<UserProfileResponse> {
    const { data } = await http.get<UserProfileResponse>(`/api/admin/users/${userId}`)
    return data
  },

  async updateStatus(userId: number, status: AccountStatus): Promise<UserProfileResponse> {
    const { data } = await http.patch<UserProfileResponse>(`/api/admin/users/${userId}/status`, null, {
      params: { status },
    })
    return data
  },
}
