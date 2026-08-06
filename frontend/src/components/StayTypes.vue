<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpRight } from 'lucide-vue-next'
import { FALLBACK_IMAGES } from '@/lib/images'
import { gsap, ScrollTrigger, prefersReducedMotion } from '@/lib/motion'

const router = useRouter()
const root = ref<HTMLElement | null>(null)
let ctx: gsap.Context | null = null

const panels = [
  {
    title: 'Hotels',
    blurb: 'Front desks, restaurants, and someone who knows the city.',
    image: FALLBACK_IMAGES[1],
    city: 'Porto',
  },
  {
    title: 'Apartments',
    blurb: 'Your own kitchen, your own hours, for a longer stay.',
    image: FALLBACK_IMAGES[6],
    city: 'Lisbon',
  },
]

onMounted(() => {
  if (prefersReducedMotion() || !root.value) return

  ctx = gsap.context(() => {
    ScrollTrigger.batch('[data-panel]', {
      start: 'top 80%',
      once: true,
      onEnter: (batch) =>
        gsap.from(batch, { y: 60, opacity: 0, stagger: 0.12, duration: 0.9, ease: 'power3.out' }),
    })
  }, root.value)
})

onUnmounted(() => {
  ctx?.revert()
  ctx = null
})

function open(city: string) {
  router.push({ name: 'hotels', query: { city } })
}
</script>

<template>
  <section ref="root" class="bg-ink px-3 md:px-5 pb-16 md:pb-24">
    <div class="max-w-[1600px] mx-auto grid gap-4 md:gap-5 md:grid-cols-2">
      <button
        v-for="panel in panels"
        :key="panel.title"
        data-panel
        type="button"
        class="relative h-[70vh] rounded-[2rem] overflow-hidden group cursor-pointer border border-hairline text-left"
        @click="open(panel.city)"
      >
        <img
          :src="panel.image"
          :alt="`${panel.title} on Folio`"
          class="absolute inset-0 w-full h-full object-cover group-hover:scale-105 transition-transform duration-[1.2s] ease-[cubic-bezier(0.16,1,0.3,1)]"
        />
        <div
          class="absolute inset-0 bg-gradient-to-t from-ink via-ink/30 to-transparent"
          aria-hidden="true"
        />

        <div class="absolute inset-x-0 bottom-0 p-6 md:p-10 flex items-end justify-between gap-4">
          <div>
            <h3 class="font-display text-4xl text-bone">{{ panel.title }}</h3>
            <p class="text-sm font-light text-bone-dim mt-1 max-w-xs">{{ panel.blurb }}</p>
          </div>
          <ArrowUpRight
            class="w-7 h-7 text-champagne shrink-0 transition-transform group-hover:translate-x-1 group-hover:-translate-y-1"
            aria-hidden="true"
          />
        </div>
      </button>
    </div>
  </section>
</template>
