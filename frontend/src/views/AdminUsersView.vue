<script setup lang="ts">
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { ChevronRight, RotateCw, Search } from 'lucide-vue-next'
import ExtranetShell from '@/components/ExtranetShell.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { adminApi } from '@/api/admin'
import { apiErrorMessage, resolveUploadUrl } from '@/api/http'
import { accountStatusLabel, type AccountStatus, type UserProfileResponse } from '@/types/account'
import { roleLabel, type Role } from '@/types/auth'

const { t } = useI18n()

const ROLES: Role[] = ['GUEST', 'COMPANY_CLIENT', 'RECEPTIONIST', 'HOTEL_MANAGER', 'ADMIN', 'SUPPORT']
const STATUSES: AccountStatus[] = [
  'PENDING',
  'APPROVED',
  'REJECTED',
  'SUSPENDED',
  'ANONYMIZED',
  'BANNED',
  'DEACTIVATED',
  'LOCKED',
]

const STATUS_CLASSES: Record<AccountStatus, string> = {
  PENDING: 'text-amber-300/90 bg-amber-300/10 border-amber-300/20',
  APPROVED: 'text-champagne bg-champagne/10 border-champagne/25',
  REJECTED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  SUSPENDED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  ANONYMIZED: 'text-bone-dim bg-bone/5 border-hairline',
  BANNED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  DEACTIVATED: 'text-bone-dim bg-bone/5 border-hairline',
  LOCKED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
}

const search = ref('')
const role = ref<Role | ''>('')
const status = ref<AccountStatus | ''>('')
const users = ref<UserProfileResponse[]>([])
const loading = ref(true)
const error = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    users.value = await adminApi.searchUsers({
      search: search.value.trim() || undefined,
      role: role.value || undefined,
      status: status.value || undefined,
    })
  } catch (e) {
    error.value = apiErrorMessage(e, t('adminUsers.loadError'))
  } finally {
    loading.value = false
  }
}

// Debounced so every keystroke doesn't fire its own request — the dropdowns below
// trigger immediately via the watcher since a select change is already a single event.
let searchTimer: ReturnType<typeof setTimeout> | undefined
function onSearchInput() {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(load, 350)
}
onUnmounted(() => clearTimeout(searchTimer))

watch([role, status], load)
onMounted(load)
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <ExtranetShell :badge="$t('adminUsers.badge')" />

    <main class="flex-1 px-6 md:px-10 py-10 max-w-5xl mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-8">{{ $t('adminUsers.title') }}</h1>

      <div class="flex flex-wrap items-end gap-3 mb-6">
        <label class="flex flex-col gap-1 flex-1 min-w-[220px]">
          <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('adminUsers.searchLabel') }}</span>
          <div class="relative">
            <Search class="w-4 h-4 text-bone-dim absolute left-0 top-1/2 -translate-y-1/2" aria-hidden="true" />
            <input
              v-model="search"
              type="text"
              :placeholder="$t('adminUsers.searchPlaceholder')"
              class="w-full bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone placeholder:text-bone-dim/50 font-light py-1.5 pl-6 transition-colors"
              @input="onSearchInput"
            />
          </div>
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('adminUsers.roleLabel') }}</span>
          <select
            v-model="role"
            class="bg-ink-2 border border-hairline rounded-full px-3 py-1.5 text-sm text-bone outline-none focus:border-champagne transition-colors"
          >
            <option value="">{{ $t('adminUsers.anyRole') }}</option>
            <option v-for="r in ROLES" :key="r" :value="r">{{ roleLabel(r) }}</option>
          </select>
        </label>
        <label class="flex flex-col gap-1">
          <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('adminUsers.statusLabel') }}</span>
          <select
            v-model="status"
            class="bg-ink-2 border border-hairline rounded-full px-3 py-1.5 text-sm text-bone outline-none focus:border-champagne transition-colors"
          >
            <option value="">{{ $t('adminUsers.anyStatus') }}</option>
            <option v-for="s in STATUSES" :key="s" :value="s">{{ accountStatusLabel(s) }}</option>
          </select>
        </label>
      </div>

      <div v-if="loading" class="flex flex-col gap-3">
        <div v-for="i in 5" :key="i" class="h-16 rounded-xl bg-ink-2 border border-hairline animate-pulse" />
      </div>

      <div v-else-if="error" class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4">
        <p class="text-sm text-rose-300">{{ error }}</p>
        <button
          type="button"
          class="flex items-center gap-2 rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors"
          @click="load"
        >
          <RotateCw class="w-4 h-4" aria-hidden="true" />
          {{ $t('adminUsers.retry') }}
        </button>
      </div>

      <p v-else-if="!users.length" class="text-sm font-light text-bone-dim">{{ $t('adminUsers.empty') }}</p>

      <ul v-else class="flex flex-col gap-2">
        <li v-for="user in users" :key="user.id">
          <RouterLink
            :to="{ name: 'admin-user-detail', params: { id: user.id } }"
            class="flex items-center gap-4 rounded-xl bg-ink-2 border border-hairline px-4 py-3 hover:border-champagne/30 transition-colors"
          >
            <img
              v-if="user.avatarUrl"
              :src="resolveUploadUrl(user.avatarUrl)!"
              alt=""
              class="w-9 h-9 rounded-full object-cover shrink-0"
            />
            <div
              v-else
              class="w-9 h-9 rounded-full bg-champagne/15 text-champagne flex items-center justify-center text-sm font-medium shrink-0"
            >
              {{ user.firstName.charAt(0).toUpperCase() }}
            </div>

            <div class="min-w-0 flex-1">
              <p class="text-sm text-bone truncate">{{ user.firstName }} {{ user.lastName }}</p>
              <p class="text-xs font-light text-bone-dim truncate">{{ user.email }}</p>
            </div>

            <div class="hidden sm:flex items-center gap-1.5 flex-wrap max-w-[220px] justify-end">
              <span
                v-for="r in user.roles"
                :key="r"
                class="px-2 py-0.5 rounded-full text-[10px] font-medium border text-bone-dim bg-bone/5 border-hairline"
              >
                {{ roleLabel(r) }}
              </span>
            </div>

            <span
              class="px-2.5 py-1 rounded-full text-[11px] font-medium border shrink-0"
              :class="STATUS_CLASSES[user.accountStatus]"
            >
              {{ accountStatusLabel(user.accountStatus) }}
            </span>

            <ChevronRight class="w-4 h-4 text-bone-dim shrink-0" aria-hidden="true" />
          </RouterLink>
        </li>
      </ul>
    </main>

    <SiteFooter />
  </div>
</template>
