export type CompanyStatus = 'ACTIVE' | 'PENDING_VERIFICATION' | 'INACTIVE' | 'SUSPENDED' | 'BLACKLISTED' | 'CLOSED'

export interface CompanyResponse {
  id: number
  name: string
  legalName: string
  email: string
  phone: string
  address: string
  city: string
  country: string
  webSite: string | null
  logoUrl: string | null
  status: CompanyStatus
}
