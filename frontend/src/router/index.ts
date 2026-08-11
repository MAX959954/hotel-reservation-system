import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAuthModalStore } from '@/stores/authModal'
import { useCompanyStore } from '@/stores/company'
import { ScrollTrigger, scrollToTop } from '@/lib/motion'
import type { Role } from '@/types/auth'

declare module 'vue-router' {
  interface RouteMeta {
    requiresAuth?: boolean
    /** Any one of these platform roles is enough — see beforeEach below. Checked after
     *  requiresAuth, so a role-gated route implies auth is required too. */
    requiresRole?: Role[]
    /** Fallback when requiresRole doesn't match: company-level membership is its own,
     *  separate role system (CompanyRole, not the platform Roles enum) — someone who is
     *  only staff on a company (invited by email, accepted or not) never gets a matching
     *  platform role from that alone, so a platform-only check would permanently lock
     *  invited staff out of the very page that shows their invite. 'any' accepts any
     *  membership regardless of status (an unaccepted invite still needs to reach the
     *  accept banner); 'manager' requires an ACTIVE OWNER/MANAGER membership.
     */
    allowCompanyRole?: 'any' | 'manager'
  }
}

const router = createRouter({
  history: createWebHistory(),
  // Honour the browser's remembered offset on back/forward. Forcing { top: 0 }
  // unconditionally would drop anyone returning from a stay at the top of the page,
  // losing their place in the 400vh flythrough they came from.
  scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    {
      path: '/hotels',
      name: 'hotels',
      component: () => import('@/views/HotelsView.vue'),
      // A translation key, not the literal English word — meta is set outside any i18n
      // context, so HotelsView resolves this itself via t(route.meta.catalogLabel).
      meta: { catalogLabel: 'hotels.stays' },
    },
    { path: '/hotels/:id', name: 'hotel', component: () => import('@/views/HotelDetailView.vue') },
    {
      // Same component as /hotels, filtered server-side to PropertyType.APARTMENT — a
      // real column as of migration V10, not a label pretending to be a filter.
      path: '/apartments',
      name: 'apartments',
      component: () => import('@/views/HotelsView.vue'),
      meta: { catalogLabel: 'hotels.apartments', defaultType: 'APARTMENT' },
    },
    { path: '/journal', name: 'journal', component: () => import('@/views/JournalView.vue') },
    {
      path: '/bookings',
      name: 'bookings',
      component: () => import('@/views/BookingsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/notifications',
      name: 'notifications',
      component: () => import('@/views/NotificationsView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/manage/bookings',
      name: 'manage-bookings',
      component: () => import('@/views/ManageBookingsView.vue'),
      meta: { requiresAuth: true, requiresRole: ['HOTEL_MANAGER', 'RECEPTIONIST', 'ADMIN'], allowCompanyRole: 'any' },
    },
    {
      // OWNER/MANAGER-only in practice (enforced server-side too) — allowCompanyRole:
      // 'manager' is what actually lets an invited-but-not-platform-roled company
      // OWNER/MANAGER in; requiresRole alone would only ever match ADMIN/HOTEL_MANAGER.
      path: '/manage/hotels',
      name: 'manage-hotels',
      component: () => import('@/views/ManageHotelsView.vue'),
      meta: { requiresAuth: true, requiresRole: ['HOTEL_MANAGER', 'ADMIN'], allowCompanyRole: 'manager' },
    },
    {
      // Any signed-in guest can apply — this is the application form itself, not a
      // HOTEL_MANAGER-only page (nobody has that role yet at the point they'd use this).
      path: '/become-a-host',
      name: 'become-a-host',
      component: () => import('@/views/BecomeHostView.vue'),
      meta: { requiresAuth: true },
    },
    {
      path: '/admin/applications',
      name: 'admin-applications',
      component: () => import('@/views/AdminApplicationsView.vue'),
      meta: { requiresAuth: true, requiresRole: ['ADMIN'] },
    },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: () => import('@/views/NotFoundView.vue') },
  ],
})

/**
 * There is no login page — a one-time code in a modal is the only way in. So a guarded
 * route opens that modal over wherever the user already is and only proceeds once they
 * are actually signed in; redirecting to a route that does not exist would dead-end them.
 */
router.beforeEach(async (to) => {
  if (!to.meta.requiresAuth && !to.meta.requiresRole) return true

  const auth = useAuthStore()
  if (!auth.isAuthenticated) {
    const authModal = useAuthModalStore()
    const signedIn = await authModal.prompt(to.fullPath)
    if (!signedIn) return false
  }

  // No dedicated "forbidden" page yet — bouncing home is at least never a dead end,
  // unlike leaving someone on a page whose data calls will just 403 silently underneath.
  if (to.meta.requiresRole && !to.meta.requiresRole.some((role) => auth.hasRole(role))) {
    if (!to.meta.allowCompanyRole) return { name: 'home' }

    const company = useCompanyStore()
    await company.load()
    const allowed = to.meta.allowCompanyRole === 'manager' ? company.managesAny : company.hasAny
    if (!allowed) return { name: 'home' }
  }

  return true
})

// Lenis owns the scroll position, so a plain router-driven reset fights it. Track
// history navigations explicitly: on back/forward leave the restored offset alone and
// just re-measure the triggers against it; on a fresh navigation, jump to the top.
let restoringHistory = false
if (typeof window !== 'undefined') {
  window.addEventListener('popstate', () => {
    restoringHistory = true
  })
}

router.afterEach(async () => {
  if (restoringHistory) {
    restoringHistory = false
    // Let the restored view lay out before the pinned sections re-measure.
    requestAnimationFrame(() => ScrollTrigger.refresh())
    return
  }
  scrollToTop()
})

export default router
