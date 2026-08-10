<script setup lang="ts">
import { Minus, Plus } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    modelValue: number
    min?: number
    max?: number
    ariaLabel?: string
  }>(),
  { min: 1, max: undefined, ariaLabel: 'Quantity' },
)
const emit = defineEmits<{ 'update:modelValue': [number] }>()

function dec() {
  if (props.modelValue > props.min) emit('update:modelValue', props.modelValue - 1)
}
function inc() {
  if (props.max == null || props.modelValue < props.max) emit('update:modelValue', props.modelValue + 1)
}
</script>

<template>
  <div class="flex items-center gap-2.5" role="group" :aria-label="ariaLabel">
    <button
      type="button"
      :disabled="modelValue <= min"
      class="w-6 h-6 shrink-0 rounded-full border border-hairline flex items-center justify-center text-bone-dim hover:text-bone hover:border-champagne-dim transition-colors disabled:opacity-30 disabled:pointer-events-none"
      aria-label="Decrease"
      @click="dec"
    >
      <Minus class="w-3 h-3" aria-hidden="true" />
    </button>
    <span class="text-sm text-bone w-4 text-center tabular-nums" aria-live="polite">{{ modelValue }}</span>
    <button
      type="button"
      :disabled="max != null && modelValue >= max"
      class="w-6 h-6 shrink-0 rounded-full border border-hairline flex items-center justify-center text-bone-dim hover:text-bone hover:border-champagne-dim transition-colors disabled:opacity-30 disabled:pointer-events-none"
      aria-label="Increase"
      @click="inc"
    >
      <Plus class="w-3 h-3" aria-hidden="true" />
    </button>
  </div>
</template>
