<script setup lang="ts">
import { Coins } from 'lucide-vue-next'
import { ACTIVE_CITIES } from '@/lib/cities'
import { useCurrencyStore } from '@/stores/currency'
import { useSettingsModalStore } from '@/stores/settingsModal'

const year = new Date().getFullYear()
const currency = useCurrencyStore()
const settingsModal = useSettingsModalStore()

// A handful of the register's real cities, not all ten — a footer column is a shortcut,
// not a second copy of the Cities menu.
const footerCities = ACTIVE_CITIES.slice(0, 5)
</script>

<template>
  <footer class="bg-ink border-t border-hairline">
    <div class="max-w-[1600px] mx-auto px-6 md:px-10 py-14 grid gap-10 sm:grid-cols-2 lg:grid-cols-4">
      <div class="lg:col-span-1">
        <span class="font-display text-3xl text-bone">FOLIO</span>
        <p class="text-sm font-light text-bone-dim mt-3 max-w-xs leading-relaxed">
          A register of independently run hotels and apartments. Every stay belongs to the
          people who run it, not to us.
        </p>
      </div>

      <div>
        <h3 class="text-[11px] font-medium uppercase tracking-[0.12em] text-bone-dim mb-4">Explore</h3>
        <ul class="flex flex-col gap-3 text-sm font-light text-bone-dim">
          <li><RouterLink to="/hotels" class="hover:text-bone transition-colors">Stays</RouterLink></li>
          <li><RouterLink to="/apartments" class="hover:text-bone transition-colors">Apartments</RouterLink></li>
          <li><RouterLink to="/journal" class="hover:text-bone transition-colors">Journal</RouterLink></li>
        </ul>
      </div>

      <div>
        <h3 class="text-[11px] font-medium uppercase tracking-[0.12em] text-bone-dim mb-4">Cities</h3>
        <ul class="flex flex-col gap-3 text-sm font-light text-bone-dim">
          <li v-for="c in footerCities" :key="c.name">
            <RouterLink :to="{ name: 'hotels', query: { city: c.name } }" class="hover:text-bone transition-colors">
              {{ c.name }}
            </RouterLink>
          </li>
        </ul>
      </div>

      <div>
        <h3 class="text-[11px] font-medium uppercase tracking-[0.12em] text-bone-dim mb-4">Support</h3>
        <ul class="flex flex-col gap-3 text-sm font-light text-bone-dim">
          <li>
            <a href="mailto:support@folio.example" class="hover:text-bone transition-colors">Contact support</a>
          </li>
          <li><RouterLink to="/bookings" class="hover:text-bone transition-colors">Manage a booking</RouterLink></li>
        </ul>
      </div>
    </div>

    <div class="border-t border-hairline">
      <div
        class="max-w-[1600px] mx-auto px-6 md:px-10 py-6 flex flex-col sm:flex-row items-center justify-between gap-4 text-xs font-light text-bone-dim"
      >
        <span>© {{ year }} Folio</span>

        <!-- Prices throughout the app are EUR, the actual charged currency; this only
             opens the same estimate picker described in the account menu — no separate
             conversion logic lives here. -->
        <button
          type="button"
          class="flex items-center gap-1.5 rounded-full border border-hairline px-3 py-1.5 hover:border-champagne-dim hover:text-bone transition-colors"
          @click="settingsModal.openTab('currency')"
        >
          <Coins class="w-3.5 h-3.5 text-champagne" aria-hidden="true" />
          {{ currency.info.code }}
        </button>
      </div>
    </div>
  </footer>
</template>
