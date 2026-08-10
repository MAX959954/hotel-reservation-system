<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { MapPin, Search, Users } from 'lucide-vue-next'
import { addDaysIso, todayIso } from '@/lib/dates'
import { QUICK_CITIES } from '@/lib/cities'
import NumberStepper from './NumberStepper.vue'
import DatePicker from './DatePicker.vue'

const router = useRouter()

const city = ref('')
const checkIn = ref(addDaysIso(todayIso(), 1))
const checkOut = ref(addDaysIso(todayIso(), 3))
const guests = ref(2)

function submit() {
  const target = city.value.trim()
  if (!target) return
  router.push({
    name: 'hotels',
    query: { city: target, checkIn: checkIn.value, checkOut: checkOut.value, guests: guests.value },
  })
}

function pickCity(name: string) {
  city.value = name
  submit()
}
</script>

<template>
  <div class="w-full max-w-3xl px-6 mt-8 md:mt-10">
    <form
      class="flex flex-col md:flex-row items-stretch md:items-center gap-2 md:gap-0 p-2 rounded-[1.5rem] md:rounded-full bg-bone/8 backdrop-blur-2xl border border-hairline shadow-[0_20px_60px_-20px_rgba(0,0,0,0.8)]"
      @submit.prevent="submit"
    >
      <label class="flex items-center gap-2 px-3 py-2 flex-1 min-w-0">
        <MapPin class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
        <span class="sr-only">{{ $t('search.cityLabel') }}</span>
        <input
          v-model="city"
          type="text"
          :placeholder="$t('search.wherePlaceholder')"
          class="bg-transparent outline-none text-sm text-bone placeholder:text-bone-dim/70 w-full font-light"
        />
      </label>

      <div class="hidden md:block w-px h-8 bg-hairline" aria-hidden="true" />

      <div class="flex items-center gap-2 px-3 py-2 flex-1 min-w-0">
        <DatePicker
          v-model="checkIn"
          :min="todayIso()"
          :aria-label="$t('search.checkInLabel')"
          class="flex-1 min-w-0"
        />
        <span class="text-bone-dim/50 text-xs" aria-hidden="true">→</span>
        <DatePicker
          v-model="checkOut"
          :min="checkIn"
          :aria-label="$t('search.checkOutLabel')"
          class="flex-1 min-w-0"
        />
      </div>

      <div class="hidden md:block w-px h-8 bg-hairline" aria-hidden="true" />

      <div class="flex items-center gap-2 px-3 py-2 w-full md:w-auto">
        <Users class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
        <NumberStepper v-model="guests" :min="1" :aria-label="$t('search.guestsLabel')" />
      </div>

      <button
        type="submit"
        class="shrink-0 flex items-center justify-center gap-2 bg-champagne text-ink rounded-full px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors"
      >
        <Search class="w-4 h-4" aria-hidden="true" />
        {{ $t('search.searchButton') }}
      </button>
    </form>

    <div class="flex flex-wrap items-center justify-center gap-2 mt-4">
      <button
        v-for="name in QUICK_CITIES"
        :key="name"
        type="button"
        class="px-3 py-1.5 rounded-full text-xs font-light text-bone-dim bg-bone/5 border border-hairline hover:border-champagne-dim hover:text-bone transition-colors"
        @click="pickCity(name)"
      >
        {{ name }}
      </button>
    </div>
  </div>
</template>
