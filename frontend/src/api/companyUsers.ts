import { http } from './http'
import type { CompanyUserResponse } from '@/types/companyUser'

export const companyUsersApi = {
  /** Every company the current user is staff on, any status (ACTIVE, INVITED, ...). */
  async getMine(): Promise<CompanyUserResponse[]> {
    const { data } = await http.get<CompanyUserResponse[]>('/company_users/me')
    return data
  },

  async acceptInvite(id: number): Promise<CompanyUserResponse> {
    const { data } = await http.patch<CompanyUserResponse>(`/company_users/${id}/accept`)
    return data
  },
}
