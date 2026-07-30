<script setup lang="ts">
import { computed } from 'vue'
import type { Amenity } from '../types/hotel'

const props = defineProps<{ amenity: Amenity }>()

// Simple single-stroke marks in the register's own hand — brass ink on the page,
// not a borrowed icon-font set. Each is a small enough shape to sit inline with a label.
const paths: Record<Amenity, string> = {
  WIFI: 'M4 10c5.5-5 10.5-5 16 0 M7 13c3.5-3 6.5-3 10 0 M10.3 16c1.2-1 2.2-1 3.4 0 M12 19h.01',
  BREAKFAST: 'M5 9h11v3a5.5 5.5 0 0 1-5.5 5.5A5.5 5.5 0 0 1 5 12V9Z M16 10h1.5a2 2 0 0 1 0 4H16 M8 5.5c-.7.6-.7 1.4 0 2 M11 5.5c-.7.6-.7 1.4 0 2 M4 19.5h13',
  AIR_CONDITIONING: 'M12 3v18 M4.5 6.5l15 11 M4.5 17.5l15-11 M9 4.5 12 7l3-2.5 M9 19.5 12 17l3 2.5 M4.8 8.6 8 12l-3.2 3.4 M19.2 8.6 16 12l3.2 3.4',
  PARKING: 'M6 4h12v16H6V4Z M9.5 7.5h3.2a2.6 2.6 0 0 1 0 5.2H9.5v5.3',
  POOL: 'M3 8c2.2 1.6 4.4 1.6 6.6 0s4.4-1.6 6.6 0 4.4 1.6 6.6 0 M3 14c2.2 1.6 4.4 1.6 6.6 0s4.4-1.6 6.6 0 4.4 1.6 6.6 0 M3 20c2.2 1.6 4.4 1.6 6.6 0s4.4-1.6 6.6 0 4.4 1.6 6.6 0',
  GYM: 'M3 12h3 M18 12h3 M6 9v6 M18 9v6 M9 12h6 M9 8v8 M15 8v8',
  SPA: 'M12 4c3.8 3.4 5.5 6.8 5.5 9.5A5.5 5.5 0 0 1 12 19a5.5 5.5 0 0 1-5.5-5.5C6.5 10.8 8.2 7.4 12 4Z',
  BAR: 'M5 5h14l-6.2 7.2v6.3 M9.5 18.5h5 M11.8 12.2v6.3',
  RESTAURANT: 'M7 4v7a1.6 1.6 0 0 1-3.2 0V4 M5.4 4v16 M17 4c-1.4 1-2 3-2 5s.6 4 2 5v6 M17 4v10',
  ROOM_SERVICE: 'M4 17.5h16 M6 17.5A6 6 0 0 1 18 17.5 M12 10v-2 M10 8h4',
  AIRPORT_SHUTTLE: 'M4 16V9a1 1 0 0 1 1-1h12l3 4v4h-2 M4 16h1 M17 16h2 M8 16.5a1.7 1.7 0 1 0 0 .1Z M15.5 16.5a1.7 1.7 0 1 0 0 .1Z',
  PET_FRIENDLY: 'M12 14.2c-2.6 0-4.6 1.7-4.6 3.5 0 1.1 1 1.8 2.2 1.4.8-.3 1.6-.5 2.4-.5s1.6.2 2.4.5c1.2.4 2.2-.3 2.2-1.4 0-1.8-2-3.5-4.6-3.5Z M8.2 10.6a1.6 2 0 1 1-3.2 0 1.6 2 0 0 1 3.2 0Z M12.6 8.7a1.6 2 0 1 1-3.2 0 1.6 2 0 0 1 3.2 0Z M17 10.6a1.6 2 0 1 1-3.2 0 1.6 2 0 0 1 3.2 0Z M19.5 14a1.6 2 0 1 1-3.2 0 1.6 2 0 0 1 3.2 0Z',
  ELEVATOR: 'M5 3.5h14v17H5V3.5Z M9.5 10.5 12 8l2.5 2.5 M9.5 15.5 12 18l2.5-2.5',
  LAUNDRY: 'M5 3.5h14v17H5V3.5Z M8 6.2h.01 M11 6.2h.01 M12 15a3.6 3.6 0 1 0 0-7.2 3.6 3.6 0 0 0 0 7.2Z M10.6 11.4a1.7 2 0 0 0 2.8 0',
  WORKSPACE: 'M4 15.5V6h16v9.5 M2.5 18.5h19l-1.5-3h-16l-1.5 3Z',
  TV: 'M4 5h16v11H4V5Z M9 19.5h6 M12 16v3.5',
  COFFEE_MAKER: 'M7 4h8v3H7V4Z M6 7h10v10a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V7Z M16 9.5h1.5a2 2 0 0 1 0 4H16',
  HAIR_DRYER: 'M4 9a5 5 0 0 1 5-5h2a5 5 0 0 1 0 10h-1v5.5a1.3 1.3 0 0 1-2.6 0V14 M19 7v4',
  LUGGAGE_STORAGE: 'M6 8h12v12H6V8Z M9.5 8V6a1 1 0 0 1 1-1h3a1 1 0 0 1 1 1v2 M6 13h12',
  ACCESSIBLE: 'M13.2 5.2a1.6 1.6 0 1 1-3.2 0 1.6 1.6 0 0 1 3.2 0Z M11.6 8v4.5l4 1.8-.7 1.7-4.6-2-1 6.3H7.4l1.1-7 2-1.4V9.5H8.5V8h3.1Z M14 18.8a4.3 4.3 0 1 1 1-8.4',
  EV_CHARGING: 'M13 3 5 13.5h5L9 21l8-10.5h-5L13 3Z',
  NON_SMOKING: 'M4 14h9 M15.5 14h1 M18.5 14h1 M4 17h13.5 M19 17h1 M4.5 4.5l15 15',
}

const path = computed(() => paths[props.amenity])
</script>

<template>
  <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" stroke-width="1.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    <path :d="path" />
  </svg>
</template>
