import { i18n } from '@/i18n'
import { humanise } from './room'
import type { Role } from './auth'

export type AccountStatus =
  | 'PENDING'
  | 'APPROVED'
  | 'REJECTED'
  | 'SUSPENDED'
  | 'ANONYMIZED'
  | 'BANNED'
  | 'DEACTIVATED'
  | 'LOCKED'

export function accountStatusLabel(status: string): string {
  const key = `accountStatus.${status}`
  const translated = i18n.global.t(key)
  return translated === key ? humanise(status) : translated
}

export interface UserProfileResponse {
  id: number
  firstName: string
  lastName: string
  email: string
  phone: string | null
  /** Read-only — age-verified at registration, not editable from the client. */
  dateOfBirth: string | null
  emailVerified: boolean
  accountStatus: AccountStatus
  roles: Role[]
  createdAt: string
  avatarUrl: string | null
}

export interface UpdateProfileRequest {
  firstName: string
  lastName: string
  phone?: string
}

export interface ChangePasswordRequest {
  /** Blank for a Google-only account that has never set a password. */
  currentPassword: string
  newPassword: string
}
