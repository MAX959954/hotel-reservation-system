import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useAuthModalStore } from '@/stores/authModal'
import { ScrollTrigger, scrollToTop } from '@/lib/motion'

const router = createRouter({
  history: createWebHistory(),
  // Honour the browser's remembered offset on back/forward. Forcing { top: 0 }
  // unconditionally would drop anyone returning from a stay at the top of the page,
  // losing their place in the 400vh flythrough they came from.
  scrollBehavior: (_to, _from, savedPosition) => savedPosition ?? { top: 0 },
  routes: [
    { path: '/', name: 'home', component: () => import('@/views/HomeView.vue') },
    { path: '/hotels', name: 'hotels', component: () => import('@/views/HotelsView.vue') },
    { path: '/hotels/:id', name: 'hotel', component: () => import('@/views/HotelDetailView.vue') },
    {
      path: '/bookings',
      name: 'bookings',
      component: () => import('@/views/BookingsView.vue'),
      meta: { requiresAuth: true },
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
  if (!to.meta.requiresAuth) return true

  const auth = useAuthStore()
  if (auth.isAuthenticated) return true

  const authModal = useAuthModalStore()
  return await authModal.prompt(to.fullPath)
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
