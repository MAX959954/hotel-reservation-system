<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { Mail, MapPin, Phone, RotateCw } from 'lucide-vue-next'
import Navbar from '@/components/Navbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import BookingModal from '@/components/BookingModal.vue'
import { hotelsApi } from '@/api/hotels'
import { roomsApi } from '@/api/rooms'
import { apiErrorMessage } from '@/api/http'
import { hotelImage, onImageError } from '@/lib/images'
import { useAuthStore } from '@/stores/auth'
import { useAuthModalStore } from '@/stores/authModal'
import { useCurrencyStore } from '@/stores/currency'
import { useHotelsStore } from '@/stores/hotels'
import type { HotelResponse } from '@/types/hotel'
import type { RoomResponse } from '@/types/room'
import { humanise, roomTypeLabel } from '@/types/room'
import {
  CHECK_IN_TIME,
  CHECK_OUT_TIME,
  addDaysIso,
  todayIso,
  toLocalDateTime,
} from '@/lib/dates'

const route = useRoute()
const auth = useAuthStore()
const authModal = useAuthModalStore()
const currency = useCurrencyStore()
const hotelsStore = useHotelsStore()

const hotel = ref<HotelResponse | null>(null)
const rooms = ref<RoomResponse[]>([])
const loading = ref(true)
const error = ref('')

const bookingRoom = ref<RoomResponse | null>(null)
const bookingOpen = ref(false)

// `||`, not `??`: an empty-string checkIn/checkOut reaches this page whenever it's
// opened from a browse that never had a date search (see HotelsView's RouterLink), and
// `??` only falls back on null/undefined — an empty string would sail through and
// produce an unparsable "T15:00:00" (no date) for the backend.
const checkIn = computed(() => String(route.query.checkIn || '') || addDaysIso(todayIso(), 1))
const checkOut = computed(() => String(route.query.checkOut || '') || addDaysIso(todayIso(), 3))
const guests = computed(() => Number(route.query.guests) || 1)

async function load() {
  error.value = ''
  const id = route.params.id as string

  // The flythrough warms this cache as each stay takes the front of the stream, so
  // arriving from there renders the header immediately instead of flashing a skeleton
  // for a request that already completed.
  const prefetched = hotelsStore.cached(Number(id))
  if (prefetched) hotel.value = prefetched
  loading.value = true

  try {
    if (!prefetched) hotel.value = await hotelsApi.getById(id)
    if (hotel.value) hotelsStore.put(hotel.value)
    rooms.value = await roomsApi.getAvailable(
      id,
      toLocalDateTime(checkIn.value, CHECK_IN_TIME),
      toLocalDateTime(checkOut.value, CHECK_OUT_TIME),
      guests.value || undefined,
    )
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not load this stay.')
  } finally {
    loading.value = false
  }
}

async function reserve(room: RoomResponse) {
  if (!auth.isAuthenticated) {
    const ok = await authModal.prompt()
    if (!ok) return
  }
  bookingRoom.value = room
  bookingOpen.value = true
}

onMounted(load)
watch(() => route.params.id, load)
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <div class="relative">
      <div class="absolute inset-x-0 top-0 z-20">
        <Navbar />
      </div>

      <div v-if="hotel" class="relative h-[60vh] overflow-hidden">
        <img
          :src="hotelImage(hotel.imageUrl, hotel.id)"
          :alt="`${hotel.name} in ${hotel.city}`"
          class="absolute inset-0 w-full h-full object-cover animate-kenburns"
          @error="onImageError($event, hotel.id)"
        />
        <div
          class="absolute inset-0 bg-gradient-to-t from-ink via-ink/50 to-ink/70"
          aria-hidden="true"
        />
        <div class="absolute inset-x-0 bottom-0 p-6 md:p-10 max-w-[1600px] mx-auto w-full">
          <span
            v-if="hotel.status !== 'ACTIVE'"
            class="inline-block mb-3 px-3 py-1 rounded-full text-[11px] font-medium text-amber-300/90 bg-amber-300/10 border border-amber-300/20"
          >
            {{ humanise(hotel.status) }}
          </span>
          <h1 class="font-display text-5xl md:text-7xl text-bone leading-[0.95]">{{ hotel.name }}</h1>
          <div class="flex flex-wrap items-center gap-x-6 gap-y-2 mt-4 text-sm font-light text-bone-dim">
            <span class="flex items-center gap-2">
              <MapPin class="w-4 h-4 text-champagne" aria-hidden="true" />
              {{ hotel.address }}, {{ hotel.city }}, {{ hotel.country }}
            </span>
            <span v-if="hotel.phone" class="flex items-center gap-2">
              <Phone class="w-4 h-4 text-champagne" aria-hidden="true" />
              {{ hotel.phone }}
            </span>
            <span v-if="hotel.email" class="flex items-center gap-2">
              <Mail class="w-4 h-4 text-champagne" aria-hidden="true" />
              {{ hotel.email }}
            </span>
          </div>
        </div>
      </div>

      <div v-else class="h-[60vh] bg-ink-2 animate-pulse" />
    </div>

    <main class="flex-1 px-6 md:px-10 py-10 max-w-[1600px] mx-auto w-full">
      <div
        v-if="error"
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

      <template v-else-if="hotel">
        <p v-if="hotel.description" class="text-base font-light text-bone-dim max-w-2xl leading-relaxed">
          {{ hotel.description }}
        </p>

        <div v-if="hotel.amenities?.length" class="flex flex-wrap gap-2 mt-6">
          <span
            v-for="amenity in hotel.amenities"
            :key="amenity"
            class="px-3 py-1.5 rounded-full text-xs font-light text-bone-dim bg-bone/5 border border-hairline"
          >
            {{ humanise(amenity) }}
          </span>
        </div>

        <h2 class="font-display text-3xl text-bone mt-12 mb-5">Available rooms</h2>

        <div v-if="loading" class="flex flex-col gap-3">
          <div v-for="i in 3" :key="i" class="h-24 rounded-[1.25rem] bg-ink-2 border border-hairline animate-pulse" />
        </div>

        <p v-else-if="!rooms.length" class="text-sm font-light text-bone-dim">
          Nothing free for these dates. Try a different range.
        </p>

        <div v-else class="flex flex-col gap-3">
          <div
            v-for="room in rooms"
            :key="room.id"
            class="flex flex-wrap items-center justify-between gap-4 p-5 rounded-[1.25rem] bg-ink-2 border border-hairline hover:border-champagne-dim transition-colors"
          >
            <div>
              <p class="font-display text-xl text-bone">{{ roomTypeLabel(room.type) }}</p>
              <p class="text-xs font-light text-bone-dim mt-1">
                Room {{ room.number }} · Floor {{ room.floor }} · Sleeps {{ room.capacity }}
              </p>
            </div>
            <div class="flex items-center gap-5">
              <p class="text-right">
                <span class="font-display text-2xl text-bone">{{ currency.format(room.pricePerNight) }}</span>
                <span class="text-xs text-bone-dim"> / night</span>
                <span v-if="currency.estimate(room.pricePerNight)" class="block text-[11px] font-light text-bone-dim">
                  {{ currency.estimate(room.pricePerNight) }}
                </span>
              </p>
              <button
                type="button"
                class="rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors"
                @click="reserve(room)"
              >
                Reserve
              </button>
            </div>
          </div>
        </div>
      </template>
    </main>

    <SiteFooter />

    <BookingModal
      :open="bookingOpen"
      :room="bookingRoom"
      :hotel-name="hotel?.name ?? ''"
      :initial-check-in="checkIn"
      :initial-check-out="checkOut"
      :initial-guests="guests"
      @close="bookingOpen = false"
      @booked="load"
    />
  </div>
</template>
