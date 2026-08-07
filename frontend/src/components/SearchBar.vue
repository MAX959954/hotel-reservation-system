<script setup lang="ts">
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { CalendarDays, MapPin, Search, Users } from 'lucide-vue-next'
import { addDaysIso, todayIso } from '@/lib/dates'
import { QUICK_CITIES } from '@/lib/cities'

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
        <span class="sr-only">City</span>
        <input
          v-model="city"
          type="text"
          placeholder="Where to?"
          class="bg-transparent outline-none text-sm text-bone placeholder:text-bone-dim/70 w-full font-light"
        />
      </label>

      <div class="hidden md:block w-px h-8 bg-hairline" aria-hidden="true" />

      <div class="flex items-center gap-2 px-3 py-2 flex-1 min-w-0">
        <CalendarDays class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
        <label class="flex-1 min-w-0">
          <span class="sr-only">Check in</span>
          <input
            v-model="checkIn"
            type="date"
            :min="todayIso()"
            class="bg-transparent outline-none text-sm text-bone w-full font-light"
          />
        </label>
        <span class="text-bone-dim/50 text-xs" aria-hidden="true">→</span>
        <label class="flex-1 min-w-0">
          <span class="sr-only">Check out</span>
          <input
            v-model="checkOut"
            type="date"
            :min="checkIn"
            class="bg-transparent outline-none text-sm text-bone w-full font-light"
          />
        </label>
      </div>

      <div class="hidden md:block w-px h-8 bg-hairline" aria-hidden="true" />

      <label class="flex items-center gap-2 px-3 py-2 w-full md:w-28">
        <Users class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
        <span class="sr-only">Guests</span>
        <input
          v-model.number="guests"
          type="number"
          min="1"
          class="bg-transparent outline-none text-sm text-bone w-full font-light"
        />
      </label>

      <button
        type="submit"
        class="shrink-0 flex items-center justify-center gap-2 bg-champagne text-ink rounded-full px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors"
      >
        <Search class="w-4 h-4" aria-hidden="true" />
        Search
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
