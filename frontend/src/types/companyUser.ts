export type CompanyRole = 'OWNER' | 'MANAGER' | 'RECEPTIONIST' | 'ACCOUNTANT' | 'STAFF'

export type CompanyUserStatus = 'ACTIVE' | 'INVITED' | 'PENDING_APPROVAL' | 'INACTIVE' | 'SUSPENDED' | 'REMOVED'

/** A user's membership in one company — one row per (user, company) pair. */
export interface CompanyUserResponse {
  id: number
  userId: number
  userEmail: string
  companyId: number
  companyName: string
  companyRole: CompanyRole
  status: CompanyUserStatus
  invitedAt: string
  joinedAt: string | null
}

/** Roles the `/api/bookings/company/{id}` and confirm/checkIn/complete/cancel actions accept. */
export const STAFF_ROLES: CompanyRole[] = ['OWNER', 'MANAGER', 'RECEPTIONIST']
