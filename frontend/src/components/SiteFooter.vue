<script setup lang="ts">
const year = new Date().getFullYear()
</script>

<template>
  <footer class="bg-ink border-t border-hairline px-6 md:px-10 py-12">
    <div class="max-w-[1600px] mx-auto flex flex-col md:flex-row md:items-end justify-between gap-8">
      <div>
        <span class="font-display text-3xl text-bone">FOLIO</span>
        <p class="text-sm font-light text-bone-dim mt-2 max-w-sm">
          A register of independently run hotels and apartments. Every stay belongs to the people
          who run it, not to us.
        </p>
      </div>

      <div class="flex flex-wrap gap-x-10 gap-y-3 text-sm font-light text-bone-dim">
        <RouterLink to="/" class="hover:text-bone transition-colors">Home</RouterLink>
        <RouterLink to="/bookings" class="hover:text-bone transition-colors">My bookings</RouterLink>
        <span>© {{ year }}</span>
      </div>
    </div>
  </footer>
</template>
