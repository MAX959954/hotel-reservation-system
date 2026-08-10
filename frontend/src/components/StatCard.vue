<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { ArrowUpRight } from 'lucide-vue-next'
import { gsap, prefersReducedMotion } from '@/lib/motion'

const root = ref<HTMLElement | null>(null)

onMounted(() => {
  if (prefersReducedMotion() || !root.value) return
  gsap.from(root.value, { x: -24, opacity: 0, duration: 0.8, delay: 0.2, ease: 'power3.out' })
})
</script>

<template>
  <div
    ref="root"
    class="absolute bottom-28 right-4 left-auto md:left-6 md:right-auto md:bottom-6 lg:bottom-10 lg:left-10 p-4 lg:p-5 rounded-[1.2rem] lg:rounded-[2rem] bg-bone/8 backdrop-blur-xl border border-hairline flex flex-col gap-3 min-w-[150px] lg:min-w-[190px] w-fit"
  >
    <div class="flex flex-col gap-1">
      <span class="font-display text-3xl md:text-4xl text-bone tracking-tight">4.8</span>
      <span class="text-[10px] md:text-[11px] font-light text-bone-dim uppercase tracking-[0.14em]">
        {{ $t('stats.rating') }}
      </span>
    </div>

    <!-- No cross-hotel reviews page exists yet, so this points at the same catalog
         everyone browses from — with no city/type filter it's already sorted by star
         rating descending (see HotelsView's loadAll), which is the closest honest match
         to "read reviews" without inventing a page or fabricating a review feed. -->
    <RouterLink
      to="/hotels"
      class="flex items-center bg-bone rounded-full pl-1.5 pr-5 py-1.5 gap-2 hover:bg-champagne-bright transition-colors self-start group"
    >
      <span class="bg-ink/10 p-1 rounded-full flex items-center justify-center">
        <ArrowUpRight class="w-3.5 h-3.5 text-ink" aria-hidden="true" />
      </span>
      <span class="text-[13px] font-medium text-ink">{{ $t('stats.readReviews') }}</span>
    </RouterLink>
  </div>
</template>
