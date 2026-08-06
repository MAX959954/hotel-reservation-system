<script setup lang="ts">
import { onMounted, ref } from 'vue'
import Navbar from './Navbar.vue'
import HeroBadge from './HeroBadge.vue'
import SearchBar from './SearchBar.vue'
import StatCard from './StatCard.vue'
import CornerPanel from './CornerPanel.vue'
import { HERO_IMAGE } from '@/lib/images'
import { gsap, prefersReducedMotion } from '@/lib/motion'

const heading = ref<HTMLElement | null>(null)
const subheading = ref<HTMLElement | null>(null)

onMounted(() => {
  if (prefersReducedMotion()) return

  if (heading.value) {
    gsap.from(heading.value, {
      opacity: 0,
      y: 28,
      filter: 'blur(12px)',
      duration: 1,
      ease: 'power3.out',
    })
  }
  if (subheading.value) {
    gsap.from(subheading.value, { opacity: 0, duration: 0.9, delay: 0.25, ease: 'power2.out' })
  }
})
</script>

<template>
  <div class="w-full h-screen flex items-center justify-center p-3 md:p-5 bg-ink">
    <section
      class="relative w-full max-w-[1600px] h-full rounded-[1.5rem] md:rounded-[2.5rem] overflow-hidden flex flex-col items-center bg-ink-2 group"
    >
      <img
        :src="HERO_IMAGE"
        alt=""
        class="absolute inset-0 w-full h-full object-cover object-center z-0 animate-kenburns"
      />

      <div
        class="absolute inset-0 z-[1] bg-gradient-to-b from-ink/85 via-ink/40 to-ink/95"
        aria-hidden="true"
      />
      <div
        class="absolute inset-0 z-[1] bg-gradient-to-r from-ink/70 to-transparent"
        aria-hidden="true"
      />

      <div class="relative z-10 w-full h-full flex flex-col items-center">
        <Navbar />

        <div class="w-full flex flex-col items-center pt-10 md:pt-16 px-6 text-center max-w-4xl">
          <HeroBadge />

          <h1
            ref="heading"
            class="font-display text-5xl sm:text-6xl md:text-7xl lg:text-[88px] font-normal text-bone mb-3 tracking-[-0.02em] leading-[0.98]"
          >
            Rooms <em class="text-champagne not-italic">worth the journey</em>
          </h1>

          <p
            ref="subheading"
            class="text-sm sm:text-base md:text-lg text-bone-dim leading-relaxed max-w-xl font-light"
          >
            Independently run hotels and apartments across 12 cities. One register, no noise.
          </p>
        </div>

        <SearchBar />

        <StatCard />
        <CornerPanel />
      </div>
    </section>
  </div>
</template>
