import { http } from './http'
import type { CompanyResponse, CompanyStatus } from '@/types/company'

export const companiesApi = {
  async getByStatus(status: CompanyStatus): Promise<CompanyResponse[]> {
    const { data } = await http.get<CompanyResponse[]>(`/api/companies/status/${status}`)
    return data
  },
}
