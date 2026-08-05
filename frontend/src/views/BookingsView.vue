<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { bookingsApi } from '../api/bookings'
import { useAuthStore } from '../stores/auth'
import type { BookingResponse } from '../types/booking'

const { t, locale } = useI18n()
const auth = useAuthStore()

const bookings = ref<BookingResponse[]>([])
const loading = ref(false)
const error = ref('')

async function loadBookings(userId: number) {
  loading.value = true
  error.value = ''
  try {
    bookings.value = await bookingsApi.getByUser(userId)
  } catch (err: any) {
    error.value = err.response?.data?.message ?? err.message ?? t('bookings.errorFallback')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  // This route is requiresAuth-gated, so userId should always be set here; a stale
  // pre-userId session (saved before this field existed) is the one edge case —
  // logging out and back in repopulates it.
  if (auth.userId) {
    loadBookings(auth.userId)
  } else {
    error.value = t('bookings.errorFallback')
  }
})

async function cancelBooking(id: number) {
  try {
    await bookingsApi.cancel(id)
    const booking = bookings.value.find((b) => b.id === id)
    if (booking) booking.bookingStatus = 'CANCELLED'
  } catch (err: any) {
    error.value = err.response?.data?.message ?? t('bookings.cancelErrorFallback')
  }
}
</script>

<template>
  <div class="page">
    <h1>{{ t('bookings.title') }}</h1>

    <p v-if="loading">{{ t('bookings.loading') }}</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="bookings.length === 0">{{ t('bookings.none') }}</p>

    <ul class="booking-list">
      <li v-for="booking in bookings" :key="booking.id" class="booking-card">
        <div>
          <strong>{{ booking.hotelName }}</strong> — {{ t('bookings.room', { number: booking.roomNumber }) }}
          <div>
            {{ new Date(booking.checkIn).toLocaleString(locale) }} →
            {{ new Date(booking.checkOut).toLocaleString(locale) }}
          </div>
          <div>{{ t('bookings.status', { status: booking.bookingStatus }) }}</div>
        </div>
        <button
          v-if="booking.bookingStatus === 'PENDING' || booking.bookingStatus === 'CONFIRMED'"
          @click="cancelBooking(booking.id)"
        >
          {{ t('bookings.cancel') }}
        </button>
      </li>
    </ul>
  </div>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
  padding: 2rem 1rem;
}
.error {
  color: #d33;
}
.booking-list {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.booking-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1rem;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
}
</style>
