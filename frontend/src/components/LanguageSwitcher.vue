<script setup lang="ts">
import { onMounted, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, ChevronDown, Globe } from 'lucide-vue-next'
import { SUPPORTED_LOCALES, setLocale } from '@/i18n'
import type { LocaleCode } from '@/i18n'

const { locale } = useI18n()

const open = ref(false)
const root = ref<HTMLElement | null>(null)

function toggle() {
  open.value = !open.value
}
function close() {
  open.value = false
}
function pick(code: LocaleCode) {
  setLocale(code)
  close()
}

function onDocumentClick(event: MouseEvent) {
  if (!open.value) return
  if (root.value && !root.value.contains(event.target as Node)) close()
}
function onEscape(event: KeyboardEvent) {
  if (open.value && event.key === 'Escape') close()
}

onMounted(() => {
  document.addEventListener('click', onDocumentClick)
  document.addEventListener('keydown', onEscape)
})
onUnmounted(() => {
  document.removeEventListener('click', onDocumentClick)
  document.removeEventListener('keydown', onEscape)
})
</script>

<template>
  <div ref="root" class="relative">
    <button
      type="button"
      class="flex items-center gap-1.5 rounded-full border border-hairline px-3 py-1.5 md:py-2 text-xs md:text-sm font-light text-bone-dim hover:text-bone hover:border-champagne-dim transition-colors"
      aria-haspopup="true"
      :aria-expanded="open"
      aria-label="Change language"
      @click="toggle"
    >
      <Globe class="w-3.5 h-3.5 text-champagne" aria-hidden="true" />
      <span class="uppercase">{{ locale }}</span>
      <ChevronDown class="w-3 h-3 transition-transform" :class="{ 'rotate-180': open }" aria-hidden="true" />
    </button>

    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="opacity-0 -translate-y-1"
      leave-active-class="transition duration-100 ease-in"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        role="menu"
        class="absolute top-full right-0 mt-3 w-44 rounded-2xl bg-ink-2/95 backdrop-blur-2xl border border-hairline shadow-[0_30px_80px_-20px_rgba(0,0,0,0.9)] p-2 z-20"
      >
        <button
          v-for="l in SUPPORTED_LOCALES"
          :key="l.code"
          role="menuitemradio"
          :aria-checked="locale === l.code"
          type="button"
          class="w-full flex items-center justify-between gap-2.5 px-3 py-2 rounded-lg text-sm font-light transition-colors"
          :class="locale === l.code ? 'text-bone bg-bone/8' : 'text-bone-dim hover:text-bone hover:bg-bone/5'"
          @click="pick(l.code)"
        >
          {{ l.nativeName }}
          <Check v-if="locale === l.code" class="w-3.5 h-3.5 text-champagne" aria-hidden="true" />
        </button>
      </div>
    </Transition>
  </div>
</template>
