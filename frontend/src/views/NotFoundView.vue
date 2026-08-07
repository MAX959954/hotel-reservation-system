<script setup lang="ts">
import Navbar from '@/components/Navbar.vue'
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <Navbar />
    <div class="flex-1 flex flex-col items-center justify-center text-center px-6 gap-4">
      <p class="font-display text-7xl text-champagne">404</p>
      <h1 class="font-display text-3xl text-bone">No such page in the register</h1>
      <RouterLink
        to="/"
        class="mt-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors"
      >
        Back to the start
      </RouterLink>
    </div>
  </div>
</template>
