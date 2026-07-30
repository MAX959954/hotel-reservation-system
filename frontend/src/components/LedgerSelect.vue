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
      {{ props.modelValue }}
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
  font-family: var(--mono);
  font-size: 0.78rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-dim);
  cursor: pointer;
  list-style: none;
  padding: 0.3rem 0.1rem;
  border-bottom: 1px solid transparent;
  transition: color 0.15s ease, border-color 0.15s ease;
  user-select: none;
}
.ledger-select-trigger::-webkit-details-marker {
  display: none;
}
.ledger-select-trigger::after {
  content: '▾';
  margin-left: 0.35rem;
  font-size: 0.65rem;
  color: var(--brass-dim);
}
.ledger-select-trigger:hover,
.ledger-select[open] .ledger-select-trigger {
  color: var(--brass-bright);
  border-bottom-color: var(--brass-dim);
}

.ledger-select-menu {
  position: absolute;
  top: calc(100% + 0.6rem);
  right: 0;
  z-index: 20;
  list-style: none;
  margin: 0;
  padding: 0.35rem;
  min-width: 9.5rem;
  background: var(--paper);
  background-image: repeating-linear-gradient(var(--paper) 0px, var(--paper) 24px, var(--paper-line) 25px);
  border: 1px solid var(--brass-dim);
  border-radius: 3px;
  box-shadow: 0 12px 24px -12px rgba(0, 0, 0, 0.6);
}

.ledger-select-option {
  display: block;
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
.ledger-select-option:hover {
  background: var(--paper-2);
}
.ledger-select-option.active {
  color: var(--stamp);
  font-weight: 700;
}
</style>
