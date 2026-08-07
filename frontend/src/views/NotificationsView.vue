<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { CalendarPlus, CheckCircle2, RotateCw, XCircle } from 'lucide-vue-next'
import Navbar from '@/components/Navbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { bookingsApi } from '@/api/bookings'
import { apiErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import type { BookingResponse } from '@/types/booking'

/**
 * There is no notifications table in the backend — this feed is derived from real booking
 * history (GET /api/bookings/user/{id}) rather than being backed by invented content.
 * Only events that carry a real timestamp are shown: `createdAt` (the booking was made),
 * `confirmedAt`, `cancelledAt`. Later transitions the API doesn't timestamp (checked in,
 * completed, no-show) aren't guessed at with a fabricated time — the booking's current
 * status is shown alongside its most recent timestamped event instead.
 */
interface NotificationEntry {
  bookingId: number
  hotelName: string
  event: 'requested' | 'confirmed' | 'cancelled'
  timestamp: string
  currentStatus: BookingResponse['bookingStatus']
}

const auth = useAuthStore()

const bookings = ref<BookingResponse[]>([])
const loading = ref(true)
const error = ref('')

const entries = computed<NotificationEntry[]>(() => {
  const list: NotificationEntry[] = []
  for (const b of bookings.value) {
    list.push({ bookingId: b.id, hotelName: b.hotelName, event: 'requested', timestamp: b.createdAt, currentStatus: b.bookingStatus })
    if (b.confirmedAt) {
      list.push({ bookingId: b.id, hotelName: b.hotelName, event: 'confirmed', timestamp: b.confirmedAt, currentStatus: b.bookingStatus })
    }
    if (b.cancelledAt) {
      list.push({ bookingId: b.id, hotelName: b.hotelName, event: 'cancelled', timestamp: b.cancelledAt, currentStatus: b.bookingStatus })
    }
  }
  return list.sort((a, b) => Date.parse(b.timestamp) - Date.parse(a.timestamp))
})

/** The array is sorted newest-first, so a booking's first appearance is its latest event. */
function isLatestForBooking(entry: NotificationEntry, index: number): boolean {
  return entries.value.findIndex((e) => e.bookingId === entry.bookingId) === index
}

const EVENT_COPY: Record<NotificationEntry['event'], string> = {
  requested: 'You requested a booking at',
  confirmed: 'confirmed your booking at',
  cancelled: 'cancelled the booking at',
}

async function load() {
  if (!auth.userId) return
  loading.value = true
  error.value = ''
  try {
    bookings.value = await bookingsApi.getByUser(auth.userId)
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not load your notifications.')
  } finally {
    loading.value = false
  }
}

function formatWhen(iso: string): string {
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }).format(
    new Date(iso),
  )
}

onMounted(load)
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <Navbar />

    <main class="flex-1 px-6 md:px-10 py-10 max-w-2xl mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-8">Notifications</h1>

      <div v-if="loading" class="flex flex-col gap-3">
        <div v-for="i in 4" :key="i" class="h-16 rounded-[1.25rem] bg-ink-2 border border-hairline animate-pulse" />
      </div>

      <div v-else-if="error" class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4">
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

      <p v-else-if="!entries.length" class="text-sm font-light text-bone-dim">
        Nothing yet — booking activity will show up here.
      </p>

      <ul v-else class="flex flex-col gap-3">
        <li
          v-for="(entry, i) in entries"
          :key="`${entry.bookingId}-${entry.event}`"
          class="flex items-start gap-4 p-4 rounded-[1.25rem] bg-ink-2 border border-hairline"
        >
          <span
            class="shrink-0 w-9 h-9 rounded-full flex items-center justify-center border"
            :class="{
              'border-champagne-dim bg-champagne/10': entry.event === 'requested',
              'border-emerald-400/30 bg-emerald-400/10': entry.event === 'confirmed',
              'border-rose-400/30 bg-rose-400/10': entry.event === 'cancelled',
            }"
          >
            <CalendarPlus v-if="entry.event === 'requested'" class="w-4 h-4 text-champagne" aria-hidden="true" />
            <CheckCircle2 v-else-if="entry.event === 'confirmed'" class="w-4 h-4 text-emerald-400" aria-hidden="true" />
            <XCircle v-else class="w-4 h-4 text-rose-400" aria-hidden="true" />
          </span>

          <div class="min-w-0 flex-1">
            <div class="flex items-start justify-between gap-3">
              <p class="text-sm text-bone">
                Folio {{ EVENT_COPY[entry.event] }}
                <RouterLink :to="{ name: 'bookings' }" class="text-champagne hover:text-champagne-bright transition-colors">
                  {{ entry.hotelName }}
                </RouterLink>
              </p>
              <span
                v-if="isLatestForBooking(entry, i)"
                class="shrink-0 text-[10px] font-medium uppercase tracking-wide text-bone-dim border border-hairline rounded-full px-2 py-0.5"
              >
                {{ entry.currentStatus }}
              </span>
            </div>
            <p class="text-xs font-light text-bone-dim mt-1">{{ formatWhen(entry.timestamp) }}</p>
          </div>
        </li>
      </ul>
    </main>

    <SiteFooter />
  </div>
</template>
