<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { bookingsApi } from '../api/bookings'
import type { BookingResponse } from '../types/booking'

const bookings = ref<BookingResponse[]>([])
const loading = ref(false)
const error = ref('')

// TODO: fetching by userId requires the current user's numeric id, which the
// auth store doesn't have yet (see TODO in HotelDetailView.vue). Wire this up
// once /api/auth/login exposes it — currentUserId() throws in the meantime.
async function loadBookings(userId: number) {
  loading.value = true
  error.value = ''
  try {
    bookings.value = await bookingsApi.getByUser(userId)
  } catch (err: any) {
    error.value = err.response?.data?.message ?? err.message ?? 'Could not load bookings.'
  } finally {
    loading.value = false
  }
}

function currentUserId(): number {
  throw new Error('userId is not available on the auth store yet — see TODO in loadBookings()')
}

onMounted(() => {
  try {
    loadBookings(currentUserId())
  } catch (err: any) {
    error.value = err.message
  }
})

async function cancelBooking(id: number) {
  try {
    await bookingsApi.cancel(id)
    const booking = bookings.value.find((b) => b.id === id)
    if (booking) booking.bookingStatus = 'CANCELLED'
  } catch (err: any) {
    error.value = err.response?.data?.message ?? 'Could not cancel booking.'
  }
}
</script>

<template>
  <div class="page">
    <h1>My bookings</h1>

    <p v-if="loading">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>
    <p v-else-if="bookings.length === 0">No bookings yet.</p>

    <ul class="booking-list">
      <li v-for="booking in bookings" :key="booking.id" class="booking-card">
        <div>
          <strong>{{ booking.hotelName }}</strong> — room {{ booking.roomNumber }}
          <div>{{ new Date(booking.checkIn).toLocaleString() }} → {{ new Date(booking.checkOut).toLocaleString() }}</div>
          <div>Status: {{ booking.bookingStatus }}</div>
        </div>
        <button
          v-if="booking.bookingStatus === 'PENDING' || booking.bookingStatus === 'CONFIRMED'"
          @click="cancelBooking(booking.id)"
        >
          Cancel
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
