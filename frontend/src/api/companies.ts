import { http } from './http'
import type { CompanyApplicationRequest, CompanyDocumentResponse, CompanyResponse, CompanyStatus } from '@/types/company'

export const companiesApi = {
  async getByStatus(status: CompanyStatus): Promise<CompanyResponse[]> {
    const { data } = await http.get<CompanyResponse[]>(`/api/companies/status/${status}`)
    return data
  },

  async getById(id: number): Promise<CompanyResponse> {
    const { data } = await http.get<CompanyResponse>(`/api/companies/${id}`)
    return data
  },

  /** The "become a host" application submission — lands in PENDING_VERIFICATION. */
  async create(request: CompanyApplicationRequest): Promise<CompanyResponse> {
    const { data } = await http.post<CompanyResponse>('/api/companies', request)
    return data
  },

  async approve(id: number): Promise<CompanyResponse> {
    const { data } = await http.patch<CompanyResponse>(`/api/companies/${id}/approve`)
    return data
  },

  async reject(id: number, reason?: string): Promise<CompanyResponse> {
    const { data } = await http.patch<CompanyResponse>(`/api/companies/${id}/reject`, null, {
      params: reason ? { reason } : undefined,
    })
    return data
  },

  async uploadDocument(companyId: number, file: File): Promise<CompanyDocumentResponse> {
    const form = new FormData()
    form.append('file', file)
    const { data } = await http.post<CompanyDocumentResponse>(`/api/companies/${companyId}/documents`, form, {
      headers: { 'Content-Type': 'multipart/form-data' },
    })
    return data
  },

  async getDocuments(companyId: number): Promise<CompanyDocumentResponse[]> {
    const { data } = await http.get<CompanyDocumentResponse[]>(`/api/companies/${companyId}/documents`)
    return data
  },

  /** Documents are no longer public static files — `doc.fileUrl` is an authenticated API
   *  path, so a plain <a href> (which can't send the JWT) would 401. Fetch the bytes
   *  through `http` instead and hand the browser a short-lived object URL. */
  async downloadDocument(doc: CompanyDocumentResponse): Promise<Blob> {
    const { data } = await http.get<Blob>(doc.fileUrl, { responseType: 'blob' })
    return data
  },
}
