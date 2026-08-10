import { http } from './http'
import type { CompanyRole, CompanyUserResponse } from '@/types/companyUser'

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

  async getByCompany(companyId: number): Promise<CompanyUserResponse[]> {
    const { data } = await http.get<CompanyUserResponse[]>(`/company_users/company/${companyId}`)
    return data
  },

  /** Works for an email with no Folio account yet — the invite auto-links the moment
   *  that address registers (see CompanyUserServiceImpl.linkPendingInvites). */
  async invite(companyId: number, email: string, companyRole: CompanyRole): Promise<CompanyUserResponse> {
    const { data } = await http.post<CompanyUserResponse>('/company_users', { companyId, email, companyRole })
    return data
  },

  async remove(id: number): Promise<void> {
    await http.delete(`/company_users/${id}`)
  },
}
