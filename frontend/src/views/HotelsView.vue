<script setup lang="ts">
import { ref } from 'vue'
import { hotelsApi } from '../api/hotels'
import type { HotelResponse } from '../types/hotel'

const city = ref('')
const hotels = ref<HotelResponse[]>([])
const loading = ref(false)
const error = ref('')
const searched = ref(false)

async function search() {
  if (!city.value.trim()) return
  loading.value = true
  error.value = ''
  searched.value = true
  try {
    hotels.value = await hotelsApi.getByCity(city.value.trim())
  } catch (err: any) {
    error.value = err.response?.data?.message ?? 'Could not load hotels.'
    hotels.value = []
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="page">
    <h1>Find a hotel</h1>

    <form class="search" @submit.prevent="search">
      <input v-model="city" type="text" placeholder="City, e.g. Paris" required />
      <button type="submit" :disabled="loading">{{ loading ? 'Searching…' : 'Search' }}</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-else-if="searched && !loading && hotels.length === 0">No hotels found for "{{ city }}".</p>

    <ul class="hotel-list">
      <li v-for="hotel in hotels" :key="hotel.id">
        <router-link :to="`/hotels/${hotel.id}`" class="hotel-card">
          <h2>{{ hotel.name }}</h2>
          <p>{{ hotel.city }}, {{ hotel.country }}</p>
          <p class="rating">{{ '★'.repeat(hotel.startRating) }}</p>
        </router-link>
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
.search {
  display: flex;
  gap: 0.5rem;
  margin-bottom: 1.5rem;
}
.search input {
  flex: 1;
  padding: 0.5rem;
  font-size: 1rem;
}
.search button {
  padding: 0.5rem 1rem;
  cursor: pointer;
}
.error {
  color: #d33;
}
.hotel-list {
  list-style: none;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.hotel-card {
  display: block;
  border: 1px solid #ddd;
  border-radius: 8px;
  padding: 1rem;
  text-decoration: none;
  color: inherit;
}
.hotel-card h2 {
  margin: 0 0 0.25rem;
  font-size: 1.1rem;
}
.rating {
  color: #e0a800;
}
</style>
