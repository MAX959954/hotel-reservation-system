<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { RotateCw, Star } from 'lucide-vue-next'
import Navbar from '@/components/Navbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { hotelsApi } from '@/api/hotels'
import { apiErrorMessage } from '@/api/http'
import { hotelImage, onImageError } from '@/lib/images'
import { gsap, ScrollTrigger, prefersReducedMotion } from '@/lib/motion'
import type { HotelResponse } from '@/types/hotel'

const route = useRoute()
const router = useRouter()

const hotels = ref<HotelResponse[]>([])
const loading = ref(false)
const error = ref('')

const city = computed(() => String(route.query.city ?? ''))
const checkIn = computed(() => String(route.query.checkIn ?? ''))
const checkOut = computed(() => String(route.query.checkOut ?? ''))
const guests = computed(() => String(route.query.guests ?? ''))

const quickCities = ['Porto', 'Kyoto', 'Lisbon', 'Barcelona']

let ctx: gsap.Context | null = null

async function load() {
  if (!city.value) {
    hotels.value = []
    return
  }
  loading.value = true
  error.value = ''
  try {
    hotels.value = await hotelsApi.getByCity(city.value)
    revealCards()
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not load stays for this city.')
    hotels.value = []
  } finally {
    loading.value = false
  }
}

function revealCards() {
  if (prefersReducedMotion()) return
  requestAnimationFrame(() => {
    ctx?.revert()
    ctx = gsap.context(() => {
      ScrollTrigger.batch('[data-hotel-card]', {
        start: 'top 88%',
        once: true,
        onEnter: (batch) =>
          gsap.from(batch, { y: 40, opacity: 0, stagger: 0.08, duration: 0.7, ease: 'power3.out' }),
      })
    })
  })
}

function search(nextCity: string) {
  router.push({ name: 'hotels', query: { ...route.query, city: nextCity } })
}

onMounted(load)
watch(() => route.query.city, load)
onUnmounted(() => {
  ctx?.revert()
  ctx = null
})
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <div class="sticky top-0 z-30 bg-ink/80 backdrop-blur-xl border-b border-hairline">
      <Navbar />
      <div class="px-6 md:px-10 pb-4 flex flex-wrap items-center gap-2">
        <span
          v-if="city"
          class="px-3 py-1.5 rounded-full text-xs font-light text-bone bg-bone/8 border border-hairline"
        >
          {{ city }}
        </span>
        <span
          v-if="checkIn && checkOut"
          class="px-3 py-1.5 rounded-full text-xs font-light text-bone-dim bg-bone/5 border border-hairline"
        >
          {{ checkIn }} → {{ checkOut }}
        </span>
        <span
          v-if="guests"
          class="px-3 py-1.5 rounded-full text-xs font-light text-bone-dim bg-bone/5 border border-hairline"
        >
          {{ guests }} {{ guests === '1' ? 'guest' : 'guests' }}
        </span>
      </div>
    </div>

    <main class="flex-1 px-6 md:px-10 py-10 max-w-[1600px] mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-8">
        <template v-if="city">Stays in {{ city }}</template>
        <template v-else>Where to?</template>
      </h1>

      <!-- Loading: skeletons in the real card shape, so the layout does not jump on arrival. -->
      <div v-if="loading" class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <div
          v-for="i in 6"
          :key="i"
          class="rounded-[1.25rem] overflow-hidden bg-ink-2 border border-hairline"
        >
          <div class="aspect-[4/3] animate-pulse bg-bone/5" />
          <div class="p-5 flex flex-col gap-2">
            <div class="h-4 w-2/3 rounded animate-pulse bg-bone/5" />
            <div class="h-3 w-1/3 rounded animate-pulse bg-bone/5" />
          </div>
        </div>
      </div>

      <div
        v-else-if="error"
        class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4"
      >
        <p class="text-sm text-rose-300">{{ error }}</p>
        <button
          type="button"
          class="flex items-center gap-2 rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors"
          @click="load"
        >
          <RotateCw class="w-4 h-4" aria-hidden="true" />
          Retry
        </button>
      </div>

      <div
        v-else-if="!hotels.length"
        class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4"
      >
        <p class="text-sm font-light text-bone-dim">
          <template v-if="city">No stays in {{ city }} yet.</template>
          <template v-else>Search a city to see what's on the register.</template>
        </p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="name in quickCities"
            :key="name"
            type="button"
            class="px-3 py-1.5 rounded-full text-xs font-light text-bone-dim bg-bone/5 border border-hairline hover:border-champagne-dim hover:text-bone transition-colors"
            @click="search(name)"
          >
            {{ name }}
          </button>
        </div>
      </div>

      <div v-else class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <RouterLink
          v-for="hotel in hotels"
          :key="hotel.id"
          data-hotel-card
          :to="{ name: 'hotel', params: { id: hotel.id }, query: { checkIn, checkOut, guests } }"
          class="group relative rounded-[1.25rem] overflow-hidden bg-ink-2 border border-hairline hover:border-champagne-dim transition-colors duration-500"
        >
          <div class="relative aspect-[4/3] overflow-hidden">
            <img
              :src="hotelImage(hotel.imageUrl, hotel.id)"
              :alt="`${hotel.name} in ${hotel.city}`"
              class="w-full h-full object-cover group-hover:scale-[1.04] transition-transform duration-[1.2s] ease-[cubic-bezier(0.16,1,0.3,1)]"
              loading="lazy"
              @error="onImageError($event, hotel.id)"
            />
            <span
              class="absolute top-3 right-3 flex items-center gap-1 px-2.5 py-1 rounded-full bg-ink/70 backdrop-blur-md border border-hairline text-[11px] font-medium text-champagne"
            >
              {{ hotel.startRating }}
              <Star class="w-3 h-3 fill-current" aria-hidden="true" />
            </span>
          </div>
          <div class="p-5">
            <h2 class="font-display text-lg text-bone">{{ hotel.name }}</h2>
            <p class="text-xs font-light text-bone-dim mt-1">
              {{ hotel.city }} · {{ hotel.country }}
            </p>
          </div>
        </RouterLink>
      </div>
    </main>

    <SiteFooter />
  </div>
</template>
