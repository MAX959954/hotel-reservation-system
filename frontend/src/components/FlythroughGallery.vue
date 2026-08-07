<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import type { ComponentPublicInstance } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowUpRight, Star } from 'lucide-vue-next'
import { hotelsApi } from '@/api/hotels'
import { apiErrorMessage } from '@/api/http'
import { useHotelsStore } from '@/stores/hotels'
import { hotelImage, onImageError } from '@/lib/images'
import { gsap, ScrollTrigger, prefersReducedMotion, scrollToY } from '@/lib/motion'
import type { HotelResponse } from '@/types/hotel'

const router = useRouter()
const hotelsStore = useHotelsStore()

const MAX_CARDS = 6
const MIN_CARDS_FOR_STREAM = 3

/** Matches the `perspective` on the stage element; the depth term in updateActive needs it. */
const PERSPECTIVE = 1200
/** A challenger must win by this much to take over, so the two never flicker at a crossover. */
const HANDOVER_MARGIN = 1.02

const stays = ref<HotelResponse[]>([])
const loading = ref(true)
const loadError = ref('')

const root = ref<HTMLElement | null>(null)
const cardRefs = ref<HTMLElement[]>([])
const activeIndex = ref<number | null>(null)

const reduced = ref(false)
const isTouch = ref(false)
/** Touch only: the active card has been tapped once and is now armed to open. */
const armed = ref(false)

let mm: gsap.MatchMedia | null = null
let timeline: gsap.core.Timeline | null = null
let trigger: ScrollTrigger | null = null
let tickerFn: ((time: number) => void) | null = null

let targetProgress = 0
let currentProgress = 0
let frozen = false

/**
 * The stream needs at least a few stays to read as a stream at all, and it cannot run
 * without motion. Everything else falls back to a plain grid of links.
 */
const useGrid = computed(
  () => reduced.value || (!loading.value && stays.value.length < MIN_CARDS_FOR_STREAM),
)

const activeStay = computed(() =>
  activeIndex.value === null ? null : (stays.value[activeIndex.value] ?? null),
)

function setCardRef(el: Element | ComponentPublicInstance | null, index: number) {
  if (el instanceof HTMLElement) cardRefs.value[index] = el
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    // Strategy: five-star stays. The API exposes no list-all endpoint — only by id,
    // city, country, company and rating — so rating is the one call that yields a
    // curated set without inventing an endpoint, and "Five-star stays" is a claim the
    // data actually supports.
    const results = await hotelsApi.getByRating(5)
    stays.value = results.slice(0, MAX_CARDS)
  } catch (e) {
    loadError.value = apiErrorMessage(e, 'Could not load featured stays.')
    stays.value = []
  } finally {
    loading.value = false
  }
}

// --- timeline geometry -------------------------------------------------------------
// Card i owns the slice [i/n * 0.8, i/n * 0.8 + 1]; slices overlap so the stream never
// empties. Everything downstream (active card, arrow-key targets) is derived from these
// two helpers rather than duplicating the arithmetic.
function sliceStart(i: number, n: number) {
  return (i / n) * 0.8
}
function sliceMidpoint(i: number, n: number) {
  return sliceStart(i, n) + 0.5
}
function timelineDuration(n: number) {
  return sliceStart(n - 1, n) + 1
}

function buildTimeline(isMobile: boolean) {
  const cards = cardRefs.value.filter(Boolean)
  const n = cards.length
  if (!n || !root.value) return

  // Deep blur over long 3D travel is the expensive part on mobile GPUs, so both are
  // dialled back rather than dropping cards (which would change the DOM per breakpoint).
  const zStart = isMobile ? -1300 : -2600
  const zEnd = isMobile ? 420 : 850
  const startBlur = isMobile ? 8 : 14
  // Touch users cannot hover to pause, so the sharp window is held open longer.
  const focusIn = isMobile ? 0.22 : 0.3
  const focusOut = isMobile ? 0.28 : 0.2

  const tl = gsap.timeline({ paused: true })

  cards.forEach((card, i) => {
    const at = sliceStart(i, n)
    const d = 1

    tl.fromTo(
      card,
      {
        z: zStart,
        scale: 0.42,
        rotateY: gsap.utils.random(-16, 16),
        x: gsap.utils.random(-260, 260),
        y: gsap.utils.random(-120, 120),
      },
      { z: zEnd, scale: 1.25, rotateY: 0, x: 0, y: 0, duration: d, ease: 'none' },
      at,
    )

    // Opacity and blur run on their own tweens so both peak mid-flight. Folding blur
    // into the position tween is what made an earlier build unreadable: it ran
    // 14px -> 6px across the whole slice, so the card was still ~10px blurred at the
    // moment it was most visible and never came into focus at all.
    tl.fromTo(card, { opacity: 0 }, { opacity: 1, duration: d * 0.25, ease: 'power1.out' }, at)
    tl.to(card, { opacity: 0, duration: d * 0.2, ease: 'power1.in' }, at + d * 0.8)

    tl.fromTo(
      card,
      { filter: `blur(${startBlur}px)` },
      { filter: 'blur(0px)', duration: d * focusIn, ease: 'power2.out' },
      at,
    )
    tl.to(card, { filter: 'blur(10px)', duration: d * focusOut, ease: 'power2.in' }, at + d * (1 - focusOut))
  })

  timeline = tl

  trigger = ScrollTrigger.create({
    trigger: root.value,
    start: 'top top',
    end: 'bottom bottom',
    onUpdate: (self) => {
      targetProgress = self.progress
    },
  })

  // An explicit lerp replaces ScrollTrigger's `scrub`. Scrub writes tl.progress()
  // itself, which leaves no clean way to suspend just that write while the pointer
  // rests on a card; driving it here makes freeze-on-hover a single boolean and keeps
  // the same smoothed feel.
  tickerFn = () => {
    if (frozen) return
    currentProgress += (targetProgress - currentProgress) * 0.08
    tl.progress(gsap.utils.clamp(0, 1, currentProgress))
    updateActive(cards)
  }
  gsap.ticker.add(tickerFn)
}

/**
 * Picks whichever card actually dominates the screen, read back from the values GSAP has
 * applied — never recomputed from slice arithmetic.
 *
 * The arithmetic version was the bug: it chose the card nearest its slice *midpoint*, but
 * mid-flight is not "in front of the camera" — a card keeps travelling toward the viewer
 * for the rest of its slice and is largest at the end. So the clickable card was often a
 * small one behind the large one being looked at, and since inactive cards are
 * pointer-events:none the click fell through and opened a hotel nobody picked.
 *
 * `scale * opacity` is not enough either: under `perspective: P` a card's rendered size is
 * `scale * P / (P - z)`, and that depth term carries most of it — at P = 1200 a card at
 * z = 850 is magnified about 3.4x, which `scale` alone never sees.
 */
function updateActive(cards: HTMLElement[]) {
  let best: number | null = null
  let bestScore = 0
  let incumbentScore = 0

  cards.forEach((card, i) => {
    const opacity = Number(gsap.getProperty(card, 'opacity'))
    if (!(opacity > 0.5)) return
    const z = Number(gsap.getProperty(card, 'z'))
    const scale = Number(gsap.getProperty(card, 'scale'))
    const score = scale * (PERSPECTIVE / (PERSPECTIVE - z)) * opacity

    if (i === activeIndex.value) incumbentScore = score
    if (score > bestScore) {
      bestScore = score
      best = i
    }
  })

  if (best === activeIndex.value) return
  // Hold the current card unless the challenger is clearly ahead — but hand over at once
  // if the incumbent has dropped out of contention entirely.
  if (incumbentScore > 0 && bestScore < incumbentScore * HANDOVER_MARGIN) return

  // Written only on change: assigning every frame would re-render the list at 60fps.
  activeIndex.value = best
  armed.value = false
}

function teardown() {
  if (tickerFn) gsap.ticker.remove(tickerFn)
  tickerFn = null
  trigger?.kill()
  trigger = null
  timeline?.kill()
  timeline = null
}

onMounted(async () => {
  reduced.value = prefersReducedMotion()
  isTouch.value = window.matchMedia('(hover: none)').matches

  await load()
  await nextTick()

  if (useGrid.value) return

  // gsap.matchMedia re-runs this on breakpoint change and reverts the previous build.
  // Reading the viewport once here instead would freeze the tuning at whatever size the
  // component mounted at and stay wrong through every later resize or rotation.
  mm = gsap.matchMedia()
  mm.add({ isMobile: '(max-width: 767px)', isDesktop: '(min-width: 768px)' }, (ctx) => {
    const { isMobile } = ctx.conditions as { isMobile: boolean; isDesktop: boolean }
    buildTimeline(isMobile)
    return teardown
  })

  // Images can change layout after the trigger is created, which is the classic cause
  // of a mis-measured pin.
  ScrollTrigger.refresh()
})

onUnmounted(() => {
  teardown()
  mm?.revert()
  mm = null
})

// Warm the detail request as soon as a stay takes the front of the stream.
watch(activeStay, (stay) => {
  if (stay) hotelsStore.prefetch(stay.id)
})

// --- interaction -------------------------------------------------------------------

function freeze() {
  frozen = true
}
function thaw() {
  frozen = false
  armed.value = false
}

function open(stay: HotelResponse) {
  router.push({ name: 'hotel', params: { id: stay.id }, query: { from: 'flythrough' } })
}

function onCardActivate(index: number) {
  if (index !== activeIndex.value) return
  const stay = stays.value[index]
  if (!stay) return

  // Touch has no hover, so a scroll gesture that happens to end on a card would
  // otherwise fire a navigation nobody asked for. First tap stops the stream here,
  // second tap opens.
  if (isTouch.value && !armed.value) {
    armed.value = true
    freeze()
    return
  }
  open(stay)
}

function step(delta: number) {
  const n = stays.value.length
  if (!n || !trigger || activeIndex.value === null) return
  const next = gsap.utils.clamp(0, n - 1, activeIndex.value + delta)
  const ratio = sliceMidpoint(next, n) / timelineDuration(n)
  scrollToY(trigger.start + ratio * (trigger.end - trigger.start))
}
</script>

<template>
  <!-- Grid path: reduced motion, or too few stays to make a stream. Every card is an
       ordinary link, so the same destination is reachable with no motion at all. -->
  <section v-if="useGrid" class="bg-ink py-24 px-6">
    <h2 class="font-display text-4xl md:text-6xl text-bone text-center mb-4">Five-star stays</h2>

    <p v-if="loadError" class="text-sm font-light text-bone-dim text-center mb-10">
      {{ loadError }}
    </p>
    <p v-else-if="!stays.length" class="text-sm font-light text-bone-dim text-center mb-10">
      No featured stays on the register just now.
    </p>

    <div v-if="stays.length" class="max-w-6xl mx-auto grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
      <RouterLink
        v-for="stay in stays"
        :key="stay.id"
        :to="{ name: 'hotel', params: { id: stay.id } }"
        class="group relative aspect-[3/4] rounded-[1.5rem] overflow-hidden border border-hairline bg-ink-2 hover:border-champagne-dim transition-colors"
      >
        <img
          :src="hotelImage(stay.imageUrl, stay.id)"
          :alt="`${stay.name} in ${stay.city}`"
          class="w-full h-full object-cover"
          loading="lazy"
          @error="onImageError($event, stay.id)"
        />
        <div class="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-ink to-transparent" aria-hidden="true" />
        <div class="absolute inset-x-0 bottom-0 p-5">
          <p class="font-display text-xl text-bone">{{ stay.name }}</p>
          <p class="text-xs font-light text-bone-dim mt-0.5">
            {{ stay.startRating }} ★ · {{ stay.city }}, {{ stay.country }}
          </p>
          <span
            class="mt-3 inline-flex items-center gap-1 bg-champagne text-ink rounded-full px-3 py-1.5 text-xs font-medium"
          >
            View stay
            <ArrowUpRight class="w-3.5 h-3.5" aria-hidden="true" />
          </span>
        </div>
      </RouterLink>
    </div>
  </section>

  <section v-else ref="root" class="relative h-[400vh] bg-ink">
    <div
      class="sticky top-0 h-screen w-full overflow-hidden flex items-center justify-center"
      style="perspective: 1200px; perspective-origin: 50% 50%; transform-style: preserve-3d"
    >
      <h2
        class="absolute font-display text-[13vw] leading-none text-bone/8 select-none pointer-events-none z-0"
      >
        STAYS
      </h2>

      <p class="sr-only" aria-live="polite">
        <template v-if="activeStay">{{ activeStay.name }}, {{ activeStay.city }}</template>
      </p>

      <div v-if="loading" class="sr-only">Loading featured stays…</div>

      <div role="list" class="contents">
        <article
          v-for="(stay, i) in stays"
          :key="stay.id"
          :ref="(el) => setCardRef(el, i)"
          role="listitem"
          class="absolute w-[280px] md:w-[380px] aspect-[3/4] rounded-[1.5rem] overflow-hidden bg-ink-2 will-change-transform opacity-0 z-10 transition-[border-color,box-shadow] duration-300"
          :class="
            activeIndex === i
              ? 'border border-champagne cursor-pointer shadow-[0_0_0_1px_rgba(216,183,120,0.35),0_30px_80px_-20px_rgba(0,0,0,0.9)] pointer-events-auto'
              : 'border border-hairline pointer-events-none'
          "
          style="backface-visibility: hidden"
          :tabindex="activeIndex === i ? 0 : -1"
          :aria-current="activeIndex === i ? 'true' : undefined"
          @click="onCardActivate(i)"
          @keydown.enter.prevent="onCardActivate(i)"
          @keydown.space.prevent="onCardActivate(i)"
          @keydown.left.prevent="step(-1)"
          @keydown.right.prevent="step(1)"
          @pointerenter="!isTouch && freeze()"
          @pointerleave="!isTouch && thaw()"
          @focus="freeze()"
          @blur="thaw()"
        >
          <img
            :src="hotelImage(stay.imageUrl, stay.id)"
            :alt="`${stay.name} in ${stay.city}`"
            class="w-full h-full object-cover"
            loading="lazy"
            @error="onImageError($event, stay.id)"
          />
          <div
            class="absolute inset-x-0 bottom-0 h-1/2 bg-gradient-to-t from-ink to-transparent"
            aria-hidden="true"
          />

          <div class="absolute inset-x-0 bottom-0 p-5">
            <p class="font-display text-xl text-bone">{{ stay.name }}</p>
            <p class="text-xs font-light text-bone-dim mt-0.5 flex items-center gap-1">
              {{ stay.startRating }}
              <Star class="w-3 h-3 fill-current text-champagne" aria-hidden="true" />
              · {{ stay.city }}, {{ stay.country }}
            </p>

            <span
              v-if="activeIndex === i"
              class="mt-3 inline-flex items-center gap-1 bg-champagne text-ink rounded-full px-3 py-1.5 text-xs font-medium"
            >
              {{ armed ? 'Tap again to open' : 'View stay' }}
              <ArrowUpRight class="w-3.5 h-3.5" aria-hidden="true" />
            </span>
          </div>
        </article>
      </div>
    </div>
  </section>
</template>
