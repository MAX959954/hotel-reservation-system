<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowUpRight, ChevronDown, MapPin } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useAuthModalStore } from '@/stores/authModal'
import { ACTIVE_CITIES } from '@/lib/cities'
import AccountMenu from './AccountMenu.vue'
import LanguageSwitcher from './LanguageSwitcher.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const authModal = useAuthModalStore()

const navLinks = [
  { label: 'nav.stays', to: { name: 'hotels' } },
  { label: 'nav.apartments', to: { name: 'apartments' } },
  { label: 'nav.journal', to: { name: 'journal' } },
]

const signedIn = computed(() => auth.isAuthenticated)

// The OTP flow is the only entry point the backend has — a single "verify code" call
// tells the caller whether they were already a member or are being registered on the
// spot. Sign in and Create account are therefore the same modal with two different
// invitations to open it, not two different flows; `intent` only changes step 1's copy.
async function openAuth(intent: 'signin' | 'register' = 'signin') {
  const ok = await authModal.prompt(null, intent)
  if (ok) router.push('/bookings')
}

// --- Cities popup -------------------------------------------------------------------

const citiesOpen = ref(false)
const citiesRoot = ref<HTMLElement | null>(null)

function toggleCities() {
  citiesOpen.value = !citiesOpen.value
}
function closeCities() {
  citiesOpen.value = false
}

function onDocumentClick(event: MouseEvent) {
  if (!citiesOpen.value) return
  if (citiesRoot.value && !citiesRoot.value.contains(event.target as Node)) closeCities()
}
function onEscape(event: KeyboardEvent) {
  if (citiesOpen.value && event.key === 'Escape') closeCities()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onEscape)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <nav class="flex items-center justify-between py-6 px-6 md:px-10 w-full relative z-10">
    <RouterLink to="/" class="flex-1">
      <span class="font-display text-2xl tracking-tight text-bone">FOLIO</span>
    </RouterLink>

    <ul class="hidden md:flex items-center gap-9 text-bone-dim font-light text-sm">
      <li>
        <RouterLink
          :to="navLinks[0].to"
          class="hover:text-bone transition-colors"
          :class="{ 'text-bone': route.name === navLinks[0].to.name }"
        >
          {{ $t(navLinks[0].label) }}
        </RouterLink>
      </li>

      <li ref="citiesRoot" class="relative">
        <button
          type="button"
          class="cursor-pointer hover:text-bone transition-colors flex items-center gap-1"
          :class="{ 'text-bone': citiesOpen }"
          aria-haspopup="true"
          :aria-expanded="citiesOpen"
          @click="toggleCities"
        >
          {{ $t('nav.cities') }}
          <ChevronDown
            class="w-3.5 h-3.5 transition-transform"
            :class="{ 'rotate-180': citiesOpen }"
            aria-hidden="true"
          />
        </button>

        <Transition
          enter-active-class="transition duration-150 ease-out"
          enter-from-class="opacity-0 -translate-y-1"
          leave-active-class="transition duration-100 ease-in"
          leave-to-class="opacity-0"
        >
          <div
            v-if="citiesOpen"
            role="menu"
            class="absolute top-full left-0 mt-3 w-64 rounded-2xl bg-ink-2/95 backdrop-blur-2xl border border-hairline shadow-[0_30px_80px_-20px_rgba(0,0,0,0.9)] p-2"
          >
            <RouterLink
              v-for="c in ACTIVE_CITIES"
              :key="c.name"
              role="menuitem"
              :to="{ name: 'hotels', query: { city: c.name } }"
              class="flex items-center gap-2.5 px-3 py-2 rounded-lg text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
              @click="closeCities"
            >
              <MapPin class="w-3.5 h-3.5 text-champagne shrink-0" aria-hidden="true" />
              <span class="text-bone">{{ c.name }}</span>
              <span class="text-xs text-bone-dim/70">{{ c.country }}</span>
            </RouterLink>
          </div>
        </Transition>
      </li>

      <li v-for="item in navLinks.slice(1)" :key="item.label">
        <RouterLink
          :to="item.to"
          class="hover:text-bone transition-colors"
          :class="{ 'text-bone': route.name === item.to.name }"
        >
          {{ $t(item.label) }}
        </RouterLink>
      </li>
    </ul>

    <div class="flex-1 flex justify-end items-center gap-3">
      <LanguageSwitcher class="hidden sm:block" />
      <AccountMenu v-if="signedIn" />

      <template v-else>
        <button
          type="button"
          class="hidden sm:flex items-center gap-2 rounded-full border border-hairline px-4 py-1.5 md:py-2 text-xs md:text-sm font-light text-bone-dim hover:text-bone hover:border-champagne-dim transition-colors"
          @click="openAuth('register')"
        >
          {{ $t('nav.createAccount') }}
        </button>

        <button
          type="button"
          class="flex items-center bg-champagne text-ink rounded-full pl-2 pr-4 md:pr-6 py-1.5 md:py-2 gap-2 md:gap-3 hover:bg-champagne-bright hover:scale-[1.02] active:scale-[0.98] transition-all group"
          @click="openAuth('signin')"
        >
          <span class="bg-ink/15 p-1 md:p-1.5 rounded-full flex items-center justify-center">
            <ArrowUpRight class="w-4 h-4 md:w-5 md:h-5" aria-hidden="true" />
          </span>
          <span class="text-xs md:text-sm font-medium">{{ $t('nav.signIn') }}</span>
        </button>
      </template>
    </div>
  </nav>
</template>
