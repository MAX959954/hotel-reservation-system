<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpRight, ChevronDown, LogOut } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useAuthModalStore } from '@/stores/authModal'

const router = useRouter()
const auth = useAuthStore()
const authModal = useAuthModalStore()

const menu = [
  { label: 'Stays', hasDropdown: false },
  { label: 'Cities', hasDropdown: true },
  { label: 'Apartments', hasDropdown: false },
  { label: 'Journal', hasDropdown: false },
]

const signedIn = computed(() => auth.isAuthenticated)

async function onSignIn() {
  const ok = await authModal.prompt()
  if (ok) router.push('/bookings')
}
</script>

<template>
  <nav class="flex items-center justify-between py-6 px-6 md:px-10 w-full relative z-10">
    <RouterLink to="/" class="flex-1">
      <span class="font-display text-2xl tracking-tight text-bone">FOLIO</span>
    </RouterLink>

    <ul class="hidden md:flex items-center gap-9 text-bone-dim font-light text-sm">
      <li
        v-for="item in menu"
        :key="item.label"
        class="cursor-pointer hover:text-bone transition-colors flex items-center gap-1 group"
      >
        {{ item.label }}
        <ChevronDown
          v-if="item.hasDropdown"
          class="w-3.5 h-3.5 transition-transform group-hover:translate-y-0.5"
          aria-hidden="true"
        />
      </li>
    </ul>

    <div class="flex-1 flex justify-end items-center gap-3">
      <RouterLink
        v-if="signedIn"
        to="/bookings"
        class="hidden sm:inline text-sm font-light text-bone-dim hover:text-bone transition-colors"
      >
        My bookings
      </RouterLink>

      <button
        v-if="signedIn"
        type="button"
        class="flex items-center gap-2 rounded-full border border-hairline px-4 py-1.5 md:py-2 text-xs md:text-sm font-light text-bone-dim hover:text-bone hover:border-champagne-dim transition-colors"
        @click="auth.logout()"
      >
        <LogOut class="w-3.5 h-3.5" aria-hidden="true" />
        Sign out
      </button>

      <button
        v-else
        type="button"
        class="flex items-center bg-champagne text-ink rounded-full pl-2 pr-4 md:pr-6 py-1.5 md:py-2 gap-2 md:gap-3 hover:bg-champagne-bright hover:scale-[1.02] active:scale-[0.98] transition-all group"
        @click="onSignIn"
      >
        <span class="bg-ink/15 p-1 md:p-1.5 rounded-full flex items-center justify-center">
          <ArrowUpRight class="w-4 h-4 md:w-5 md:h-5" aria-hidden="true" />
        </span>
        <span class="text-xs md:text-sm font-medium">Sign in</span>
      </button>
    </div>
  </nav>
</template>
