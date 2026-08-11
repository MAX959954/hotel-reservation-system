import { defineStore } from 'pinia'
import { companyUsersApi } from '@/api/companyUsers'
import type { CompanyUserResponse } from '@/types/companyUser'

interface CompanyState {
  memberships: CompanyUserResponse[]
  loaded: boolean
}

/**
 * Whether the signed-in user is staff on any company at all — used to decide if the
 * "Manage bookings" nav link (and the panel behind it) should show up. Loaded once per
 * session and reset on sign-out/sign-in so a different account on the same browser
 * doesn't briefly see the previous user's companies.
 */
export const useCompanyStore = defineStore('company', {
  state: (): CompanyState => ({ memberships: [], loaded: false }),

  getters: {
    active: (state) => state.memberships.filter((m) => m.status === 'ACTIVE'),
    hasAny: (state) => state.memberships.some((m) => m.status === 'ACTIVE' || m.status === 'INVITED'),
    /** ACTIVE and OWNER/MANAGER specifically — the set of companies a member can actually
     *  edit hotels/rooms for, as opposed to just being staff on (see ManageHotelsView). */
    managesAny: (state) =>
      state.memberships.some((m) => m.status === 'ACTIVE' && (m.companyRole === 'OWNER' || m.companyRole === 'MANAGER')),
  },

  actions: {
    async load() {
      if (this.loaded) return
      try {
        this.memberships = await companyUsersApi.getMine()
      } catch {
        this.memberships = []
      } finally {
        this.loaded = true
      }
    },

    reset() {
      this.memberships = []
      this.loaded = false
    },
  },
})
