<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { hotelsApi } from '../api/hotels'
import { roomsApi } from '../api/rooms'
import { bookingsApi } from '../api/bookings'
import { useAuthStore } from '../stores/auth'
import type { HotelResponse } from '../types/hotel'
import type { RoomResponse } from '../types/room'

const props = defineProps<{ id: number }>()

const auth = useAuthStore()

const hotel = ref<HotelResponse | null>(null)
const rooms = ref<RoomResponse[]>([])
const loading = ref(true)
const error = ref('')

const bookingRoomId = ref<number | null>(null)
const checkIn = ref('')
const checkOut = ref('')
const guestCount = ref(1)
const bookingError = ref('')
const bookingSuccess = ref('')
const bookingLoading = ref(false)

onMounted(async () => {
  try {
    const [hotelRes, roomsRes] = await Promise.all([
      hotelsApi.getById(props.id),
      roomsApi.getAvailableForHotel(props.id),
    ])
    hotel.value = hotelRes
    rooms.value = roomsRes
  } catch (err: any) {
    error.value = err.response?.data?.message ?? 'Could not load this hotel.'
  } finally {
    loading.value = false
  }
})

function startBooking(roomId: number) {
  bookingRoomId.value = roomId
  bookingError.value = ''
  bookingSuccess.value = ''
}

async function submitBooking() {
  if (!bookingRoomId.value) return
  bookingLoading.value = true
  bookingError.value = ''
  bookingSuccess.value = ''
  try {
    // TODO: userId isn't returned by /api/auth/login or embedded in the JWT today.
    // Once the backend exposes the current user's id (e.g. via a /api/auth/me
    // endpoint or an extra claim), replace this with that value instead of
    // deriving the booking owner from the client.
    await bookingsApi.create({
      roomId: bookingRoomId.value,
      userId: currentUserId(),
      checkIn: new Date(checkIn.value).toISOString(),
      checkOut: new Date(checkOut.value).toISOString(),
      guestCount: guestCount.value,
    })
    bookingSuccess.value = 'Booking created.'
    bookingRoomId.value = null
  } catch (err: any) {
    bookingError.value = err.response?.data?.message ?? err.message ?? 'Could not create booking.'
  } finally {
    bookingLoading.value = false
  }
}

function currentUserId(): number {
  throw new Error('userId is not available on the auth store yet — see TODO in submitBooking()')
}
</script>

<template>
  <div class="page">
    <p v-if="loading">Loading…</p>
    <p v-else-if="error" class="error">{{ error }}</p>

    <template v-else-if="hotel">
      <h1>{{ hotel.name }}</h1>
      <p class="location">{{ hotel.address }}, {{ hotel.city }}, {{ hotel.country }}</p>
      <p class="rating">{{ '★'.repeat(hotel.startRating) }}</p>
      <p>{{ hotel.description }}</p>

      <h2>Available rooms</h2>
      <p v-if="rooms.length === 0">No available rooms right now.</p>

      <ul class="room-list">
        <li v-for="room in rooms" :key="room.id" class="room-card">
          <div>
            <strong>{{ room.type }}</strong> — room {{ room.number }} · up to
            {{ room.capacity }} guests
            <div class="price">${{ room.pricePerNight.toFixed(2) }} / night</div>
          </div>

          <button v-if="auth.isAuthenticated" @click="startBooking(room.id)">Book</button>
          <router-link v-else to="/login">Log in to book</router-link>

          <form
            v-if="bookingRoomId === room.id"
            class="booking-form"
            @submit.prevent="submitBooking"
          >
            <label>
              Check-in
              <input v-model="checkIn" type="datetime-local" required />
            </label>
            <label>
              Check-out
              <input v-model="checkOut" type="datetime-local" required />
            </label>
            <label>
              Guests
              <input v-model.number="guestCount" type="number" min="1" :max="room.capacity" required />
            </label>
            <button type="submit" :disabled="bookingLoading">
              {{ bookingLoading ? 'Booking…' : 'Confirm booking' }}
            </button>
          </form>
        </li>
      </ul>

      <p v-if="bookingError" class="error">{{ bookingError }}</p>
      <p v-if="bookingSuccess" class="success">{{ bookingSuccess }}</p>
    </template>
  </div>
</template>

<style scoped>
.page {
  max-width: 720px;
  margin: 0 auto;
  padding: 2rem 1rem;
}
.location {
  color: #555;
}
.rating {
  color: #e0a800;
}
.room-list {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.room-card {
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1rem;
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  align-items: flex-start;
}
.price {
  color: #555;
  font-size: 0.9rem;
}
.booking-form {
  display: flex;
  gap: 0.75rem;
  align-items: flex-end;
  flex-wrap: wrap;
}
.booking-form label {
  display: flex;
  flex-direction: column;
  font-size: 0.85rem;
  gap: 0.25rem;
}
.error {
  color: #d33;
}
.success {
  color: #2a2;
}
</style>
