export type CompanyStatus =
  | 'ACTIVE'
  | 'PENDING_VERIFICATION'
  | 'INACTIVE'
  | 'SUSPENDED'
  | 'BLACKLISTED'
  | 'CLOSED'
  | 'REJECTED'

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
  submittedByUserId: number | null
  bankAccountHolder: string | null
  bankIban: string | null
  rejectionReason: string | null
  status: CompanyStatus
}

/** Fields the "become a host" form collects — matches CompaniesRequest on the backend.
 *  submittedByUserId is deliberately absent: the server sets it from the JWT. */
export interface CompanyApplicationRequest {
  name: string
  legalName: string
  email: string
  phone: string
  address: string
  city: string
  country: string
  webSite: string
  logoUrl?: string
  bankAccountHolder?: string
  bankIban?: string
}

export interface CompanyDocumentResponse {
  id: number
  companyId: number
  fileUrl: string
  originalFilename: string
  uploadedAt: string
}
