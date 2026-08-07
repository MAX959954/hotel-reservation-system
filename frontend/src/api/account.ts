import { http } from './http'
import type { ChangePasswordRequest, UpdateProfileRequest, UserProfileResponse } from '@/types/account'

export const accountApi = {
  async getProfile(): Promise<UserProfileResponse> {
    const { data } = await http.get<UserProfileResponse>('/api/users/me')
    return data
  },

  async updateProfile(request: UpdateProfileRequest): Promise<UserProfileResponse> {
    const { data } = await http.patch<UserProfileResponse>('/api/users/me', request)
    return data
  },

  async changePassword(request: ChangePasswordRequest): Promise<void> {
    await http.patch('/api/users/me/password', request)
  },

  async uploadAvatar(file: File): Promise<UserProfileResponse> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await http.post<UserProfileResponse>('/api/users/me/avatar', form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },
}
