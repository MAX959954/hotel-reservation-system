<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { RotateCw, Star } from 'lucide-vue-next'
import Navbar from '@/components/Navbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { hotelsApi } from '@/api/hotels'
import { apiErrorMessage } from '@/api/http'
import { hotelImage, onImageError } from '@/lib/images'
import { gsap, ScrollTrigger, prefersReducedMotion } from '@/lib/motion'
import { ACTIVE_CITIES, QUICK_CITIES } from '@/lib/cities'
import type { HotelResponse, PropertyType } from '@/types/hotel'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const hotels = ref<HotelResponse[]>([])
const loading = ref(false)
const error = ref('')

const city = computed(() => String(route.query.city ?? ''))
const checkIn = computed(() => String(route.query.checkIn ?? ''))
const checkOut = computed(() => String(route.query.checkOut ?? ''))
const guests = computed(() => String(route.query.guests ?? ''))

// Shared by /hotels ("Stays") and /apartments: the heading and default filter differ,
// the component and loading logic don't. See router/index.ts for the route meta —
// catalogLabel there is a translation key ('hotels.stays'/'hotels.apartments'), not text.
const catalogLabel = computed(() => t((route.meta.catalogLabel as string) ?? 'hotels.stays'))
const propertyType = computed(() => (route.query.type as PropertyType) || (route.meta.defaultType as PropertyType) || null)

let ctx: gsap.Context | null = null

async function load() {
  loading.value = true
  error.value = ''
  try {
    if (city.value) {
      hotels.value = await hotelsApi.getByCity(city.value)
    } else if (propertyType.value) {
      // Real filter as of migration V10 — PropertyType.APARTMENT is an actual column,
      // not a label pretending to slice the hotel catalog.
      hotels.value = await hotelsApi.getByType(propertyType.value)
    } else {
      hotels.value = await loadAll()
    }
    revealCards()
  } catch (e) {
    error.value = apiErrorMessage(e, t('hotels.loadError'))
    hotels.value = []
  } finally {
    loading.value = false
  }
}

/**
 * "Stays" with no filter at all still has no "list every hotel" endpoint to call, so
 * browsing everything means asking every known city in parallel and merging what comes
 * back. `allSettled` rather than `all`: one slow or failing city should thin the results,
 * not blank the whole page for everyone else's stays.
 */
async function loadAll(): Promise<HotelResponse[]> {
  const results = await Promise.allSettled(ACTIVE_CITIES.map((c) => hotelsApi.getByCity(c.name)))
  const fulfilled = results.filter(
    (r): r is PromiseFulfilledResult<HotelResponse[]> => r.status === 'fulfilled',
  )
  if (!fulfilled.length && results.length) {
    throw new Error('Could not reach the register.')
  }
  return fulfilled.flatMap((r) => r.value).sort((a, b) => b.startRating - a.startRating)
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
  router.push({ name: route.name === 'apartments' ? 'apartments' : 'hotels', query: { ...route.query, city: nextCity } })
}

onMounted(load)
watch(() => [route.name, route.query.city, route.query.type], load)
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
          {{ guests }} {{ guests === '1' ? $t('hotels.guest') : $t('hotels.guests') }}
        </span>
      </div>
    </div>

    <main class="flex-1 px-6 md:px-10 py-10 max-w-[1600px] mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-8">
        <template v-if="city">{{ $t('hotels.inCity', { catalog: catalogLabel, city }) }}</template>
        <template v-else>{{ catalogLabel }}</template>
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
          {{ $t('hotels.retry') }}
        </button>
      </div>

      <div
        v-else-if="!hotels.length"
        class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4"
      >
        <p class="text-sm font-light text-bone-dim">
          <template v-if="city">{{ $t('hotels.noResultsInCity', { catalog: catalogLabel.toLowerCase(), city }) }}</template>
          <template v-else>{{ $t('hotels.nothingOnRegister') }}</template>
        </p>
        <div class="flex flex-wrap gap-2">
          <button
            v-for="name in QUICK_CITIES"
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
          :to="{
            name: 'hotel',
            params: { id: hotel.id },
            query: checkIn && checkOut ? { checkIn, checkOut, guests } : {},
          }"
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
            <!-- Only shown when browsing the mixed catalog: on /apartments every card is
                 already an apartment, so the label would just be noise. -->
            <span
              v-if="!propertyType"
              class="absolute top-3 left-3 px-2.5 py-1 rounded-full bg-ink/70 backdrop-blur-md border border-hairline text-[11px] font-medium text-bone-dim"
            >
              {{ hotel.propertyType === 'APARTMENT' ? $t('hotels.propertyApartment') : $t('hotels.propertyHotel') }}
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
