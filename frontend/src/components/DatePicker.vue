<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-vue-next'

const props = withDefaults(
  defineProps<{
    modelValue: string
    min?: string
    max?: string
    placeholder?: string
    ariaLabel?: string
    /** Providing this swaps the plain "August 2026" header for month/year <select>s — for
     *  a birthdate spanning decades, clicking "previous month" a few hundred times isn't
     *  a real navigation option. Omit it for near-future pickers (search, booking dates). */
    yearRange?: [number, number]
  }>(),
  { min: undefined, max: undefined, placeholder: 'Select date', ariaLabel: 'Date', yearRange: undefined },
)
const emit = defineEmits<{ 'update:modelValue': [string] }>()

const WEEKDAYS = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat']
const MONTH_NAMES = [
  'January', 'February', 'March', 'April', 'May', 'June',
  'July', 'August', 'September', 'October', 'November', 'December',
]

function parseISO(iso: string | undefined): Date | null {
  if (!iso) return null
  const [y, m, d] = iso.split('-').map(Number)
  if (!y || !m || !d) return null
  return new Date(y, m - 1, d)
}
function toISO(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}
function stripTime(d: Date): Date {
  return new Date(d.getFullYear(), d.getMonth(), d.getDate())
}

const open = ref(false)
const openUpward = ref(false)
const root = ref<HTMLElement | null>(null)

/** Rough panel height (header + weekday row + up to 6 day rows + padding) — enough to
 *  decide "does this fit below the trigger" without waiting for a real layout pass. */
const ESTIMATED_PANEL_HEIGHT = 360

function decideDirection() {
  const rect = root.value?.getBoundingClientRect()
  if (!rect) {
    openUpward.value = false
    return
  }
  const spaceBelow = window.innerHeight - rect.bottom
  const spaceAbove = rect.top
  // Flip up only when there's genuinely more room that way — otherwise a trigger near
  // the very top of the page would just push the panel off-screen in the other direction.
  openUpward.value = spaceBelow < ESTIMATED_PANEL_HEIGHT && spaceAbove > spaceBelow
}

const selected = computed(() => parseISO(props.modelValue))
const minDate = computed(() => parseISO(props.min))
const maxDate = computed(() => parseISO(props.max))

const viewYear = ref(0)
const viewMonth = ref(0)

function resetView() {
  const base = selected.value ?? minDate.value ?? new Date()
  viewYear.value = base.getFullYear()
  viewMonth.value = base.getMonth()
}
resetView()

watch(
  () => props.modelValue,
  () => {
    if (!open.value) resetView()
  },
)

function toggle() {
  if (!open.value) {
    resetView()
    decideDirection()
  }
  open.value = !open.value
}
function close() {
  open.value = false
}

function prevMonth() {
  if (viewMonth.value === 0) {
    viewMonth.value = 11
    viewYear.value -= 1
  } else {
    viewMonth.value -= 1
  }
}
function nextMonth() {
  if (viewMonth.value === 11) {
    viewMonth.value = 0
    viewYear.value += 1
  } else {
    viewMonth.value += 1
  }
}

const years = computed(() => {
  if (!props.yearRange) return []
  const [a, b] = props.yearRange
  const list: number[] = []
  for (let y = a; y <= b; y++) list.push(y)
  return list
})

interface DayCell {
  date: Date
  iso: string
  inMonth: boolean
  disabled: boolean
}

const weeks = computed<DayCell[][]>(() => {
  const firstOfMonth = new Date(viewYear.value, viewMonth.value, 1)
  const gridStart = new Date(viewYear.value, viewMonth.value, 1 - firstOfMonth.getDay())
  const min = minDate.value ? stripTime(minDate.value) : null
  const max = maxDate.value ? stripTime(maxDate.value) : null

  const cells: DayCell[] = []
  for (let i = 0; i < 42; i++) {
    const d = new Date(gridStart)
    d.setDate(gridStart.getDate() + i)
    const disabled = (min !== null && d < min) || (max !== null && d > max)
    cells.push({ date: d, iso: toISO(d), inMonth: d.getMonth() === viewMonth.value, disabled })
  }

  const rows: DayCell[][] = []
  for (let i = 0; i < 6; i++) rows.push(cells.slice(i * 7, i * 7 + 7))
  return rows
})

function pick(cell: DayCell) {
  if (cell.disabled) return
  emit('update:modelValue', cell.iso)
  close()
}

function isSelected(cell: DayCell) {
  return !!props.modelValue && props.modelValue === cell.iso
}
const todayIsoValue = toISO(new Date())
function isToday(cell: DayCell) {
  return cell.iso === todayIsoValue
}

const displayLabel = computed(() => {
  if (!selected.value) return props.placeholder
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }).format(selected.value)
})

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
      class="flex items-center gap-2 w-full text-left"
      :aria-label="ariaLabel"
      aria-haspopup="true"
      :aria-expanded="open"
      @click="toggle"
    >
      <CalendarDays class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
      <span class="text-sm font-light truncate" :class="selected ? 'text-bone' : 'text-bone-dim/70'">
        {{ displayLabel }}
      </span>
    </button>

    <Transition
      enter-active-class="transition duration-150 ease-out"
      enter-from-class="opacity-0 -translate-y-1"
      leave-active-class="transition duration-100 ease-in"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        class="absolute z-30 left-0 w-72 rounded-2xl bg-ink-2/95 backdrop-blur-2xl border border-hairline shadow-[0_30px_80px_-20px_rgba(0,0,0,0.9)] p-4 max-h-[80vh] overflow-y-auto"
        :class="openUpward ? 'bottom-full mb-3' : 'top-full mt-3'"
      >
        <div class="flex items-center justify-between gap-2 mb-3">
          <button
            type="button"
            class="w-7 h-7 rounded-full flex items-center justify-center text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors shrink-0"
            aria-label="Previous month"
            @click="prevMonth"
          >
            <ChevronLeft class="w-4 h-4" aria-hidden="true" />
          </button>

          <div v-if="yearRange" class="flex items-center gap-1 min-w-0">
            <select
              v-model.number="viewMonth"
              class="bg-transparent text-sm text-bone outline-none cursor-pointer"
            >
              <option v-for="(m, i) in MONTH_NAMES" :key="m" :value="i" class="bg-ink-2 text-bone">
                {{ m }}
              </option>
            </select>
            <select
              v-model.number="viewYear"
              class="bg-transparent text-sm text-bone outline-none cursor-pointer"
            >
              <option v-for="y in years" :key="y" :value="y" class="bg-ink-2 text-bone">{{ y }}</option>
            </select>
          </div>
          <span v-else class="text-sm text-bone font-medium">{{ MONTH_NAMES[viewMonth] }} {{ viewYear }}</span>

          <button
            type="button"
            class="w-7 h-7 rounded-full flex items-center justify-center text-bone-dim hover:text-bone hover:bg-bone/5 transition-colors shrink-0"
            aria-label="Next month"
            @click="nextMonth"
          >
            <ChevronRight class="w-4 h-4" aria-hidden="true" />
          </button>
        </div>

        <div class="grid grid-cols-7 gap-1 text-center text-[10px] uppercase tracking-wide text-bone-dim mb-1">
          <span v-for="d in WEEKDAYS" :key="d">{{ d }}</span>
        </div>

        <div class="grid grid-cols-7 gap-1">
          <template v-for="(row, ri) in weeks" :key="ri">
            <button
              v-for="cell in row"
              :key="cell.iso"
              type="button"
              :disabled="cell.disabled"
              class="aspect-square rounded-lg text-xs flex items-center justify-center transition-colors"
              :class="[
                !cell.inMonth ? 'text-bone-dim/30' : 'text-bone',
                cell.disabled ? 'opacity-30 cursor-not-allowed' : 'hover:bg-bone/10 cursor-pointer',
                isSelected(cell) ? '!bg-champagne !text-ink font-medium' : '',
                isToday(cell) && !isSelected(cell) ? 'ring-1 ring-champagne-dim' : '',
              ]"
              @click="pick(cell)"
            >
              {{ cell.date.getDate() }}
            </button>
          </template>
        </div>
      </div>
    </Transition>
  </div>
</template>
