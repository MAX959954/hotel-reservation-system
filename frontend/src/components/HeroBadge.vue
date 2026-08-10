<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { Sparkles } from 'lucide-vue-next'
import { gsap, prefersReducedMotion } from '@/lib/motion'

const root = ref<HTMLElement | null>(null)

onMounted(() => {
  if (prefersReducedMotion() || !root.value) return
  gsap.from(root.value, { opacity: 0, y: 16, duration: 0.6, ease: 'power2.out' })
})
</script>

<template>
  <div
    ref="root"
    class="flex items-center gap-2 px-4 py-2 rounded-full bg-bone/8 backdrop-blur-md border border-hairline mx-auto mb-5 w-fit"
  >
    <Sparkles class="w-3.5 h-3.5 text-champagne" aria-hidden="true" />
    <span class="text-[13px] font-light tracking-wide text-bone-dim">
      {{ $t('hero.badge') }}
    </span>
  </div>
</template>
