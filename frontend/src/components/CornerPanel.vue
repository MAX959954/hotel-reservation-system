<script setup lang="ts">
import { ref } from 'vue'
import { ChevronRight, Play } from 'lucide-vue-next'
import TourLightbox from './TourLightbox.vue'

const tourOpen = ref(false)
</script>

<template>
  <button
    type="button"
    class="absolute bottom-0 right-0 p-3 pt-5 pl-8 sm:p-4 sm:pt-6 sm:pl-10 md:p-6 md:pt-8 md:pl-14 bg-ink rounded-tl-[1.5rem] sm:rounded-tl-[2rem] md:rounded-tl-[3.5rem] flex items-center gap-3 sm:gap-4 md:gap-6 text-left group"
    aria-label="Take the tour — 2 minute film"
    @click="tourOpen = true"
  >
    <!-- Inverse-radius masks: they paint page-background into the two outer corners so the
         panel reads as cut out of the hero rather than pasted on top of it. -->
    <div
      class="absolute -top-[1.5rem] sm:-top-[2rem] md:-top-[3.5rem] right-0 w-[1.5rem] sm:w-[2rem] md:w-[3.5rem] h-[1.5rem] sm:h-[2rem] md:h-[3.5rem] pointer-events-none"
      aria-hidden="true"
    >
      <svg width="100%" height="100%" viewBox="0 0 56 56" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M56 56V0C56 30.9279 30.9279 56 0 56H56Z" fill="#0a0a0b" />
      </svg>
    </div>
    <div
      class="absolute bottom-0 -left-[1.5rem] sm:-left-[2rem] md:-left-[3.5rem] w-[1.5rem] sm:w-[2rem] md:w-[3.5rem] h-[1.5rem] sm:h-[2rem] md:h-[3.5rem] pointer-events-none"
      aria-hidden="true"
    >
      <svg width="100%" height="100%" viewBox="0 0 56 56" fill="none" xmlns="http://www.w3.org/2000/svg">
        <path d="M56 56H0C30.9279 56 56 30.9279 56 0V56Z" fill="#0a0a0b" />
      </svg>
    </div>

    <div
      class="bg-bone/5 w-10 h-10 md:w-14 md:h-14 rounded-full flex items-center justify-center border border-hairline shrink-0 transition-colors group-hover:bg-champagne/15 group-hover:border-champagne-dim"
    >
      <Play class="w-4 h-4 md:w-5 md:h-5 text-champagne" aria-hidden="true" />
    </div>

    <div class="flex flex-col">
      <span class="text-[16px] md:text-[20px] font-display text-bone">{{ $t('tour.title') }}</span>
      <div class="flex items-center gap-1 text-bone-dim transition-colors group-hover:text-bone">
        <span class="text-[12px] md:text-[14px] font-light">{{ $t('tour.duration') }}</span>
        <ChevronRight class="w-3.5 h-3.5" aria-hidden="true" />
      </div>
    </div>
  </button>

  <TourLightbox :open="tourOpen" @close="tourOpen = false" />
</template>
