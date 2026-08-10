<script setup lang="ts">
import { watch } from 'vue'
import { TriangleAlert } from 'lucide-vue-next'
import { useConfirmModalStore } from '@/stores/confirmModal'

const modal = useConfirmModalStore()

watch(
  () => modal.open,
  (isOpen) => {
    document.body.style.overflow = isOpen ? 'hidden' : ''
  },
)
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
        v-if="modal.open"
        class="fixed inset-0 z-[60] bg-ink/70 backdrop-blur-sm flex items-center justify-center p-4"
        role="alertdialog"
        aria-modal="true"
        aria-labelledby="confirm-modal-message"
        @click.self="modal.resolve(false)"
        @keydown.esc="modal.resolve(false)"
      >
        <div
          class="w-full max-w-sm rounded-[1.75rem] bg-ink-2/95 backdrop-blur-2xl border border-hairline p-6 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)]"
        >
          <div class="flex items-start gap-3 mb-5">
            <span
              v-if="modal.danger"
              class="w-9 h-9 rounded-full bg-rose-400/10 border border-rose-400/25 flex items-center justify-center shrink-0"
            >
              <TriangleAlert class="w-4 h-4 text-rose-300" aria-hidden="true" />
            </span>
            <div>
              <h2 v-if="modal.title" class="font-display text-xl text-bone mb-1">{{ modal.title }}</h2>
              <p id="confirm-modal-message" class="text-sm font-light text-bone-dim leading-relaxed">
                {{ modal.message }}
              </p>
            </div>
          </div>

          <div class="flex items-center justify-end gap-2">
            <button
              type="button"
              class="rounded-full border border-hairline px-4 py-2 text-sm font-light text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors"
              @click="modal.resolve(false)"
            >
              {{ modal.cancelLabel }}
            </button>
            <button
              type="button"
              class="rounded-full px-4 py-2 text-sm font-medium transition-colors"
              :class="
                modal.danger
                  ? 'bg-rose-400 text-ink hover:bg-rose-300'
                  : 'bg-champagne text-ink hover:bg-champagne-bright'
              "
              @click="modal.resolve(true)"
            >
              {{ modal.confirmLabel }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
