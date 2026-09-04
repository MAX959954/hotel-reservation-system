<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { Bell, Briefcase, Building2, ChevronDown, CircleUser, Coins, Home, LayoutDashboard, LogOut, Shield, Users } from 'lucide-vue-next'
import { accountApi } from '@/api/account'
import { authApi } from '@/api/auth'
import { resolveUploadUrl } from '@/api/http'
import RolesModal from '@/components/RolesModal.vue'
import { useAuthStore } from '@/stores/auth'
import { useCompanyStore } from '@/stores/company'
import { useNotificationsStore } from '@/stores/notifications'
import { useSettingsModalStore } from '@/stores/settingsModal'

const router = useRouter()
const { t } = useI18n()
const auth = useAuthStore()
const company = useCompanyStore()
const notifications = useNotificationsStore()
const settingsModal = useSettingsModalStore()

const open = ref(false)
const rolesOpen = ref(false)
const root = ref<HTMLElement | null>(null)

const initial = () => (auth.email ? auth.email.charAt(0).toUpperCase() : '?')
const avatarSrc = () => resolveUploadUrl(auth.avatarUrl)
const badgeLabel = computed(() => (notifications.unreadCount > 9 ? '9+' : String(notifications.unreadCount)))

// Company staff (OWNER/MANAGER/RECEPTIONIST at a specific company) always qualify;
// platform-wide staff roles bypass the per-company membership check the same way the
// backend's own @PreAuthorize expressions do for these endpoints — a plain GUEST with
// neither gets no entry point into a panel the API would reject them from anyway.
const canManageBookings = computed(
  () => company.hasAny || auth.hasRole('ADMIN') || auth.hasRole('RECEPTIONIST') || auth.hasRole('HOTEL_MANAGER'),
)

const canManageHotels = computed(() => company.managesAny || auth.hasRole('ADMIN') || auth.hasRole('HOTEL_MANAGER'))

function toggle() {
  open.value = !open.value
}
function close() {
  open.value = false
}

function go(path: string) {
  close()
  router.push(path)
}
function openSettings(tab: 'personal' | 'currency') {
  close()
  settingsModal.openTab(tab)
}
function openRoles() {
  close()
  rolesOpen.value = true
}
function signOut() {
  close()
  // Best-effort: revokes the refresh token and blacklists the access token server-side
  // (see UserServiceImpl.logout) so a leaked token from this session dies immediately
  // instead of just sitting valid until it expires on its own. Local state is cleared
  // either way — the whole point of a client-side session is that it doesn't depend on
  // this call succeeding.
  const refreshToken = auth.refreshToken
  if (refreshToken) {
    authApi.logout(refreshToken).catch(() => {})
  }
  auth.logout()
  company.reset()
  notifications.reset()
  router.push('/')
}

function onDocumentClick(event: MouseEvent) {
  if (!open.value) return
  if (root.value && !root.value.contains(event.target as Node)) close()
}
function onEscape(event: KeyboardEvent) {
  if (open.value && event.key === 'Escape') close()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onEscape)
})

// The photo isn't part of the auth session, so fetch it whenever a session starts —
// this menu is mounted once for the whole app, so a later sign-in wouldn't otherwise
// re-trigger a fetch the way an onMounted-only check would. Settings keeps it in sync
// after that (e.g. a fresh upload) without needing another round trip here.
watch(
  () => auth.userId,
  (userId) => {
    if (!userId) {
      // Covers the 401 interceptor's silent logout, not just the Sign out button.
      company.reset()
      notifications.reset()
      return
    }
    if (!auth.avatarUrl) {
      // Roles ride along on the same call: like the avatar, they aren't on AuthResponse,
      // and a stale cached array would leave "Manage bookings" hidden after a role grant
      // until the user happens to open RolesModal (which does its own forced refresh).
      accountApi
        .getProfile()
        .then((profile) => {
          auth.setAvatar(profile.avatarUrl)
          auth.setRoles(profile.roles)
        })
        .catch(() => {})
    }
    company.load()
    notifications.load()
  },
  { immediate: true },
)
onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div ref="root" class="relative">
    <button
      type="button"
      class="relative flex items-center gap-2 rounded-full border border-hairline pl-1 pr-3 py-1 hover:border-champagne-dim transition-colors"
      aria-haspopup="true"
      :aria-expanded="open"
      :aria-label="notifications.unreadCount ? t('accountMenu.ariaLabelUnread', { count: badgeLabel }) : t('accountMenu.ariaLabel')"
      @click="toggle"
    >
      <span class="relative shrink-0">
        <img
          v-if="avatarSrc()"
          :src="avatarSrc()!"
          alt=""
          class="w-7 h-7 rounded-full object-cover"
          aria-hidden="true"
        />
        <span
          v-else
          class="w-7 h-7 rounded-full bg-champagne text-ink flex items-center justify-center text-xs font-medium"
          aria-hidden="true"
        >
          {{ initial() }}
        </span>
        <span
          v-if="notifications.unreadCount"
          class="absolute -top-1 -right-1 min-w-[16px] h-4 px-1 rounded-full bg-rose-400 text-ink text-[10px] font-medium leading-4 text-center border-2 border-ink"
          aria-hidden="true"
        >
          {{ badgeLabel }}
        </span>
      </span>
      <ChevronDown class="w-3.5 h-3.5 text-bone-dim transition-transform" :class="{ 'rotate-180': open }" aria-hidden="true" />
    </button>

    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="opacity-0 -translate-y-1"
      leave-active-class="transition duration-100 ease-in"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        role="menu"
        class="absolute top-full right-0 mt-3 w-60 rounded-2xl bg-ink-2/95 backdrop-blur-2xl border border-hairline shadow-[0_30px_80px_-20px_rgba(0,0,0,0.9)] p-2"
      >
        <button
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="go('/bookings')"
        >
          <Briefcase class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.myBookings') }}
        </button>
        <button
          v-if="canManageBookings"
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="go('/manage/bookings')"
        >
          <Building2 class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.manageBookings') }}
        </button>
        <button
          v-if="canManageHotels"
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="go('/manage/hotels')"
        >
          <Home class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.manageHotels') }}
        </button>
        <button
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="go('/become-a-host')"
        >
          <Home class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.becomeHost') }}
        </button>
        <button
          v-if="auth.hasRole('ADMIN')"
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="go('/admin/applications')"
        >
          <LayoutDashboard class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.admin') }}
        </button>
        <button
          v-if="auth.hasRole('ADMIN')"
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="go('/admin/users')"
        >
          <Users class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.adminUsers') }}
        </button>
        <button
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="openRoles"
        >
          <Shield class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.viewRoles') }}
        </button>

        <div class="h-px bg-hairline my-2" role="separator" />

        <button
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="go('/notifications')"
        >
          <Bell class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.notifications') }}
          <span
            v-if="notifications.unreadCount"
            class="ml-auto min-w-[18px] h-[18px] px-1 rounded-full bg-rose-400 text-ink text-[10px] font-medium leading-[18px] text-center"
          >
            {{ badgeLabel }}
          </span>
        </button>
        <button
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="openSettings('currency')"
        >
          <Coins class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.regionCurrency') }}
        </button>
        <button
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="openSettings('personal')"
        >
          <CircleUser class="w-4 h-4 text-champagne" aria-hidden="true" />
          {{ $t('accountMenu.accountSettings') }}
        </button>

        <div class="h-px bg-hairline my-2" role="separator" />

        <button
          role="menuitem"
          type="button"
          class="w-full flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
          @click="signOut"
        >
          <LogOut class="w-4 h-4" aria-hidden="true" />
          {{ $t('accountMenu.signOut') }}
        </button>
      </div>
    </Transition>

    <RolesModal :open="rolesOpen" @close="rolesOpen = false" />
  </div>
</template>
