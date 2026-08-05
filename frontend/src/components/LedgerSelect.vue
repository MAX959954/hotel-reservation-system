<script setup lang="ts">
import { ref, onBeforeUnmount, onMounted } from 'vue'

const props = defineProps<{
  modelValue: string
  options: { value: string; label: string }[]
  ariaLabel: string
}>()

const emit = defineEmits<{ 'update:modelValue': [value: string] }>()

const detailsEl = ref<HTMLDetailsElement | null>(null)

function select(value: string) {
  emit('update:modelValue', value)
  if (detailsEl.value) detailsEl.value.open = false
}

function onDocumentClick(event: MouseEvent) {
  if (detailsEl.value && !detailsEl.value.contains(event.target as Node)) {
    detailsEl.value.open = false
  }
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onBeforeUnmount(() => document.removeEventListener('click', onDocumentClick))
</script>

<template>
  <details ref="detailsEl" class="ledger-select">
    <summary class="ledger-select-trigger" :aria-label="ariaLabel">
      <span v-if="$slots.icon" class="trigger-icon"><slot name="icon" /></span>
      <span class="trigger-label">{{ props.modelValue }}</span>
      <svg class="trigger-caret" viewBox="0 0 10 6" width="8" height="5" aria-hidden="true">
        <path d="M1 1l4 4 4-4" fill="none" stroke="currentColor" stroke-width="1.3" stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </summary>
    <ul class="ledger-select-menu" role="listbox" :aria-label="ariaLabel">
      <li v-for="opt in props.options" :key="opt.value">
        <button
          type="button"
          class="ledger-select-option"
          :class="{ active: opt.value === props.modelValue }"
          role="option"
          :aria-selected="opt.value === props.modelValue"
          @click="select(opt.value)"
        >
          <span class="option-mark" aria-hidden="true">{{ opt.value === props.modelValue ? '✦' : '' }}</span>
          {{ opt.label }}
        </button>
      </li>
    </ul>
  </details>
</template>

<style scoped>
.ledger-select {
  position: relative;
}

.ledger-select-trigger {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  font-family: var(--mono);
  font-size: 0.75rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--text-dim);
  cursor: pointer;
  list-style: none;
  padding: 0.32rem 0.55rem;
  background: var(--ink-raised);
  border: 1px solid var(--border);
  border-radius: 3px;
  transition: color 0.15s ease, border-color 0.15s ease, background 0.15s ease;
  user-select: none;
}
.ledger-select-trigger::-webkit-details-marker {
  display: none;
}
.trigger-icon {
  display: inline-flex;
  color: var(--brass-dim);
  transition: color 0.15s ease;
}
.trigger-caret {
  color: var(--brass-dim);
  transition: transform 0.2s ease, color 0.15s ease;
}
.ledger-select[open] .trigger-caret {
  transform: rotate(180deg);
}

.ledger-select-trigger:hover,
.ledger-select-trigger:focus-visible,
.ledger-select[open] .ledger-select-trigger {
  color: var(--brass-bright);
  border-color: var(--brass-dim);
  background: var(--ink-cover-2);
}
.ledger-select-trigger:hover .trigger-icon,
.ledger-select-trigger:focus-visible .trigger-icon,
.ledger-select[open] .trigger-icon,
.ledger-select-trigger:hover .trigger-caret,
.ledger-select-trigger:focus-visible .trigger-caret,
.ledger-select[open] .trigger-caret {
  color: var(--brass-bright);
}
.ledger-select-trigger:focus-visible {
  outline: 2px solid var(--brass);
  outline-offset: 2px;
}

.ledger-select-menu {
  position: absolute;
  top: calc(100% + 0.6rem);
  right: 0;
  z-index: 20;
  list-style: none;
  margin: 0;
  padding: 0.35rem;
  min-width: 10rem;
  background: var(--paper);
  background-image: repeating-linear-gradient(var(--paper) 0px, var(--paper) 24px, var(--paper-line) 25px);
  border: 1px solid var(--brass-dim);
  border-radius: 3px;
  box-shadow: 0 12px 24px -12px rgba(0, 0, 0, 0.6);
}

.ledger-select-option {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  width: 100%;
  text-align: left;
  background: none;
  border: none;
  font-family: var(--mono);
  font-size: 0.8rem;
  color: var(--paper-ink);
  padding: 0.3rem 0.5rem;
  cursor: pointer;
  border-radius: 2px;
}
.option-mark {
  width: 0.9em;
  color: var(--stamp);
  font-size: 0.7rem;
}
.ledger-select-option:hover {
  background: var(--paper-2);
}
.ledger-select-option.active {
  color: var(--stamp);
  font-weight: 700;
}
</style>
