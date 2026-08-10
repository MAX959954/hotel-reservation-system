<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import AccountMenu from './AccountMenu.vue'
import LanguageSwitcher from './LanguageSwitcher.vue'
import { useAuthStore } from '@/stores/auth'

withDefaults(defineProps<{ badge?: string }>(), { badge: '' })

const route = useRoute()
const auth = useAuthStore()

// RECEPTIONIST only ever manages bookings, not hotel listings — the hotels link would
// just 404 their access on every company picker entry, so it's not worth showing them.
const showHotelsLink = computed(() => auth.hasRole('HOTEL_MANAGER') || auth.hasRole('ADMIN'))
</script>

<template>
  <div class="border-b border-hairline">
    <nav class="flex items-center justify-between py-6 px-6 md:px-10 w-full">
      <div class="flex items-center gap-8">
        <RouterLink to="/" class="flex items-center gap-3">
          <span class="font-display text-2xl tracking-tight text-bone">FOLIO</span>
          <span
            class="text-[10px] uppercase tracking-[0.14em] text-champagne border border-champagne/30 rounded-full px-2 py-0.5"
          >
            {{ badge || $t('extranet.badge') }}
          </span>
        </RouterLink>

        <ul class="hidden md:flex items-center gap-6 text-sm font-light text-bone-dim">
          <li>
            <RouterLink
              :to="{ name: 'manage-bookings' }"
              class="hover:text-bone transition-colors"
              :class="{ 'text-bone': route.name === 'manage-bookings' }"
            >
              {{ $t('extranet.navBookings') }}
            </RouterLink>
          </li>
          <li v-if="showHotelsLink">
            <RouterLink
              :to="{ name: 'manage-hotels' }"
              class="hover:text-bone transition-colors"
              :class="{ 'text-bone': route.name === 'manage-hotels' }"
            >
              {{ $t('extranet.navHotels') }}
            </RouterLink>
          </li>
        </ul>
      </div>

      <div class="flex items-center gap-4">
        <LanguageSwitcher />
        <AccountMenu />
      </div>
    </nav>
  </div>
</template>
