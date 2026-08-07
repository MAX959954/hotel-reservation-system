<script setup lang="ts">
import { computed, onUnmounted, ref, watch } from 'vue'
import { ChevronLeft, ChevronRight, X } from 'lucide-vue-next'
import { FALLBACK_IMAGES } from '@/lib/images'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const SLIDE_MS = 4000

const index = ref(0)
let timer: ReturnType<typeof setInterval> | null = null

const total = FALLBACK_IMAGES.length

function goTo(i: number) {
  index.value = (i + total) % total
  restart()
}
function next() {
  goTo(index.value + 1)
}
function prev() {
  goTo(index.value - 1)
}

function restart() {
  stop()
  timer = setInterval(next, SLIDE_MS)
}
function stop() {
  if (timer) clearInterval(timer)
  timer = null
}

function onKeydown(e: KeyboardEvent) {
  if (e.key === 'ArrowRight') next()
  else if (e.key === 'ArrowLeft') prev()
  else if (e.key === 'Escape') emit('close')
}

watch(
  () => props.open,
  (isOpen) => {
    document.body.style.overflow = isOpen ? 'hidden' : ''
    if (isOpen) {
      index.value = 0
      restart()
      window.addEventListener('keydown', onKeydown)
    } else {
      stop()
      window.removeEventListener('keydown', onKeydown)
    }
  },
)

onUnmounted(() => {
  stop()
  window.removeEventListener('keydown', onKeydown)
  document.body.style.overflow = ''
})

const progressKey = computed(() => `${index.value}-${props.open}`)
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      leave-active-class="transition duration-150 ease-in"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-50 bg-ink/95 backdrop-blur-md flex items-center justify-center"
        role="dialog"
        aria-modal="true"
        aria-label="Take the tour"
        @click.self="emit('close')"
      >
        <!-- Progress bars: one per slide, filling left-to-right in sync with autoplay —
             a slim, honest stand-in for a scrubber with 12 stills instead of real footage. -->
        <div class="absolute top-0 inset-x-0 flex gap-1.5 p-4 md:p-6 z-10">
          <div
            v-for="i in total"
            :key="i"
            class="flex-1 h-[2px] rounded-full bg-bone/20 overflow-hidden"
          >
            <div v-if="i - 1 < index" class="h-full w-full bg-champagne" />
            <div
              v-else-if="i - 1 === index"
              :key="progressKey"
              class="h-full bg-champagne animate-tour-fill"
              :style="{ animationDuration: `${SLIDE_MS}ms` }"
            />
          </div>
        </div>

        <button
          type="button"
          class="absolute top-4 right-4 md:top-6 md:right-6 z-10 text-bone-dim hover:text-bone transition-colors"
          aria-label="Close tour"
          @click="emit('close')"
        >
          <X class="w-6 h-6" aria-hidden="true" />
        </button>

        <div class="absolute top-4 left-4 md:top-6 md:left-6 z-10">
          <p class="font-display text-lg text-bone">Take the tour</p>
          <p class="text-xs font-light text-bone-dim">{{ index + 1 }} / {{ total }}</p>
        </div>

        <div class="relative w-full h-full overflow-hidden">
          <img
            v-for="(src, i) in FALLBACK_IMAGES"
            :key="src"
            :src="src"
            alt=""
            class="absolute inset-0 w-full h-full object-cover transition-opacity duration-700 ease-out"
            :class="i === index ? 'opacity-100' : 'opacity-0'"
          />
          <div class="absolute inset-0 bg-gradient-to-t from-ink/70 via-transparent to-ink/40" aria-hidden="true" />
        </div>

        <button
          type="button"
          class="absolute left-3 md:left-6 top-1/2 -translate-y-1/2 z-10 w-10 h-10 md:w-12 md:h-12 rounded-full bg-bone/10 hover:bg-bone/20 backdrop-blur-md border border-hairline flex items-center justify-center transition-colors"
          aria-label="Previous"
          @click="prev"
        >
          <ChevronLeft class="w-5 h-5 text-bone" aria-hidden="true" />
        </button>
        <button
          type="button"
          class="absolute right-3 md:right-6 top-1/2 -translate-y-1/2 z-10 w-10 h-10 md:w-12 md:h-12 rounded-full bg-bone/10 hover:bg-bone/20 backdrop-blur-md border border-hairline flex items-center justify-center transition-colors"
          aria-label="Next"
          @click="next"
        >
          <ChevronRight class="w-5 h-5 text-bone" aria-hidden="true" />
        </button>

        <p class="absolute bottom-6 md:bottom-10 inset-x-0 text-center text-sm font-light text-bone-dim px-6">
          Independently run hotels and apartments across 12 cities.
        </p>
      </div>
    </Transition>
  </Teleport>
</template>

<style scoped>
@keyframes tour-fill {
  from {
    width: 0%;
  }
  to {
    width: 100%;
  }
}
.animate-tour-fill {
  animation-name: tour-fill;
  animation-timing-function: linear;
  animation-fill-mode: forwards;
}
</style>
