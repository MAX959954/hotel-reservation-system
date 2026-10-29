<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import Navbar from '@/components/Navbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { FALLBACK_IMAGES } from '@/lib/images'
import { gsap, ScrollTrigger, prefersReducedMotion } from '@/lib/motion'

/**
 * There is no CMS or blog backend behind this page — the API only serves hotels, rooms
 * and bookings. These entries are illustrative editorial copy in the site's own voice,
 * not real published articles: no invented named journalists, no specific factual claims
 * (dates, review counts, quotes) that could be mistaken for something real. The byline is
 * the publication itself, the way a house style guide would be credited. Every text field
 * is an i18n key (locales/*.ts, section `journal`).
 */

interface Entry {
  category: string
  title: string
  excerpt: string
  image: string
}

const entries: Entry[] = [
  {
    category: 'journal.cityGuide',
    title: 'journal.kyotoTitle',
    excerpt: 'journal.kyotoExcerpt',
    image: FALLBACK_IMAGES[7],
  },
  {
    category: 'journal.fieldNotes',
    title: 'journal.frontDeskTitle',
    excerpt: 'journal.frontDeskExcerpt',
    image: FALLBACK_IMAGES[1],
  },
  {
    category: 'journal.fieldNotes',
    title: 'journal.stayLongerTitle',
    excerpt: 'journal.stayLongerExcerpt',
    image: FALLBACK_IMAGES[10],
  },
  {
    category: 'journal.cityGuide',
    title: 'journal.santoriniTitle',
    excerpt: 'journal.santoriniExcerpt',
    image: FALLBACK_IMAGES[8],
  },
  {
    category: 'journal.fieldNotes',
    title: 'journal.packingTitle',
    excerpt: 'journal.packingExcerpt',
    image: FALLBACK_IMAGES[3],
  },
  {
    category: 'journal.cityGuide',
    title: 'journal.newYorkTitle',
    excerpt: 'journal.newYorkExcerpt',
    image: FALLBACK_IMAGES[4],
  },
]

const root = ref<HTMLElement | null>(null)
let ctx: gsap.Context | null = null

onMounted(() => {
  if (prefersReducedMotion() || !root.value) return
  ctx = gsap.context(() => {
    ScrollTrigger.batch('[data-entry]', {
      start: 'top 88%',
      once: true,
      onEnter: (batch) =>
        gsap.from(batch, { y: 40, opacity: 0, stagger: 0.08, duration: 0.7, ease: 'power3.out' }),
    })
  }, root.value)
})

onUnmounted(() => {
  ctx?.revert()
  ctx = null
})
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <Navbar />

    <main ref="root" class="flex-1 px-6 md:px-10 py-10 max-w-[1600px] mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-3">{{ $t('journal.title') }}</h1>
      <p class="text-sm font-light text-bone-dim max-w-xl mb-10">
        {{ $t('journal.subtitle') }}
      </p>

      <div class="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        <article
          v-for="entry in entries"
          :key="entry.title"
          data-entry
          class="rounded-[1.25rem] overflow-hidden bg-ink-2 border border-hairline"
        >
          <div class="aspect-[4/3] overflow-hidden">
            <img
              :src="entry.image"
              alt=""
              class="w-full h-full object-cover"
              loading="lazy"
            />
          </div>
          <div class="p-5">
            <span class="text-[11px] font-medium uppercase tracking-[0.12em] text-champagne">
              {{ $t(entry.category) }}
            </span>
            <h2 class="font-display text-lg text-bone mt-2">{{ $t(entry.title) }}</h2>
            <p class="text-sm font-light text-bone-dim mt-2 leading-relaxed">{{ $t(entry.excerpt) }}</p>
            <p class="text-xs font-light text-bone-dim/70 mt-4">{{ $t('journal.byline') }}</p>
          </div>
        </article>
      </div>
    </main>

    <SiteFooter />
  </div>
</template>
