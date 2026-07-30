<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { bookingsApi } from '../api/bookings'
import { useAuthStore } from '../stores/auth'
import { useCurrencyStore } from '../stores/currency'
import type { RoomResponse } from '../types/room'

const props = defineProps<{
  hotelName: string
  rooms: RoomResponse[]
  checkIn: string
  checkOut: string
  guestCount: number
  initialRoomId?: number | null
}>()

const emit = defineEmits<{ close: [] }>()

const { t } = useI18n()
const auth = useAuthStore()
const currency = useCurrencyStore()

type Step = 'pick' | 'review' | 'done'
const step = ref<Step>('pick')
const panelRef = ref<HTMLElement | null>(null)

const selectedRoom = ref<RoomResponse | null>(
  props.initialRoomId ? props.rooms.find((r) => r.id === props.initialRoomId) ?? null : null,
)

const nights = computed(() => {
  const ms = new Date(props.checkOut).getTime() - new Date(props.checkIn).getTime()
  return Math.max(1, Math.round(ms / (1000 * 60 * 60 * 24)))
})
const total = computed(() => (selectedRoom.value ? selectedRoom.value.pricePerNight * nights.value : 0))

const acronyms: Record<string, string> = { WIFI: 'WiFi', TV: 'TV', EV: 'EV' }
function humanize(value: string) {
  return value
    .split('_')
    .map((w) => acronyms[w] ?? w[0].toUpperCase() + w.slice(1).toLowerCase())
    .join(' ')
}

function selectRoom(room: RoomResponse) {
  selectedRoom.value = room
}

function goToReview() {
  if (!selectedRoom.value) return
  step.value = 'review'
}
function goToPick() {
  step.value = 'pick'
  reserveError.value = ''
}

const specialRequest = ref('')
const reserveLoading = ref(false)
const reserveError = ref('')

function apiErrorMessage(err: any, fallback: string): string {
  const errors = err.response?.data?.errors
  if (Array.isArray(errors) && errors.length > 0) return errors.join(' ')
  return err.response?.data?.message ?? fallback
}

async function reserve() {
  if (!selectedRoom.value || !auth.userId) return
  reserveLoading.value = true
  reserveError.value = ''
  try {
    await bookingsApi.create({
      roomId: selectedRoom.value.id,
      userId: auth.userId,
      checkIn: `${props.checkIn}T15:00:00`,
      checkOut: `${props.checkOut}T11:00:00`,
      guestCount: props.guestCount,
      specialRequest: specialRequest.value.trim() || undefined,
    })
    step.value = 'done'
  } catch (err: any) {
    reserveError.value = apiErrorMessage(err, t('hotelDetail.reserve.errorFallback'))
  } finally {
    reserveLoading.value = false
  }
}

function onOverlayClick() {
  emit('close')
}
function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    emit('close')
    return
  }
  if (event.key === 'Tab' && panelRef.value) {
    const focusables = panelRef.value.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), textarea:not(:disabled), a[href]',
    )
    if (focusables.length === 0) return
    const first = focusables[0]
    const last = focusables[focusables.length - 1]
    if (event.shiftKey && document.activeElement === first) {
      event.preventDefault()
      last.focus()
    } else if (!event.shiftKey && document.activeElement === last) {
      event.preventDefault()
      first.focus()
    }
  }
}

watch(step, async () => {
  await nextTick()
  panelRef.value?.querySelector<HTMLElement>('button, input, textarea')?.focus()
})

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
  if (step.value === 'pick' && selectedRoom.value) {
    // Arrived via a specific room's "Book" button — skip straight to review.
    step.value = 'review'
  }
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <Teleport to="body">
    <div class="overlay" @mousedown.self="onOverlayClick">
      <div ref="panelRef" class="panel" role="dialog" aria-modal="true" :aria-label="t('hotelDetail.reserve.modalLabel')">
        <button
          v-if="step !== 'done'"
          type="button"
          class="close-btn"
          :aria-label="t('auth.close')"
          @click="emit('close')"
        >
          <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
            <path d="M1 1l14 14M15 1L1 15" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
          </svg>
        </button>

        <!-- Step: pick a room -->
        <div v-if="step === 'pick'" class="step">
          <p class="folio-no">{{ t('auth.folioMark') }}</p>
          <h2>{{ t('hotelDetail.reserve.modalTitle') }}</h2>
          <p class="subtitle">
            {{ t('hotelDetail.reserve.modalSubtitle', { checkIn, checkOut, guests: guestCount }) }}
          </p>

          <ul class="room-options">
            <li v-for="room in rooms" :key="room.id">
              <button
                type="button"
                class="room-option"
                :class="{ selected: selectedRoom?.id === room.id }"
                @click="selectRoom(room)"
              >
                <span class="room-option-main">
                  <strong>{{ humanize(room.type) }}</strong>
                  <span class="room-option-meta">
                    {{ t('hotelDetail.reserve.roomCapacity', { capacity: room.capacity, floor: room.floor }) }}
                  </span>
                </span>
                <span class="room-option-price">
                  {{ currency.format(room.pricePerNight * nights) }}
                  <span class="per-stay">{{ t('hotelDetail.reserve.total') }}</span>
                </span>
              </button>
            </li>
          </ul>

          <div class="modal-footer">
            <span v-if="selectedRoom" class="footer-total">
              {{ currency.format(total) }} {{ t('hotelDetail.reserve.total').toLowerCase() }}
            </span>
            <span v-else class="footer-total footer-total-empty">{{ t('hotelDetail.reserve.pickARoom') }}</span>
            <button type="button" class="brass-btn" :disabled="!selectedRoom" @click="goToReview">
              {{ t('hotelDetail.reserve.next') }}
            </button>
          </div>
        </div>

        <!-- Step: review & reserve -->
        <div v-else-if="step === 'review' && selectedRoom" class="step">
          <button type="button" class="back-btn" :aria-label="t('auth.back')" @click="goToPick">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
              <path d="M10 2L4 8l6 6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <p class="folio-no">{{ t('auth.folioMark') }}</p>
          <h2>{{ t('hotelDetail.reserve.reviewTitle') }}</h2>

          <dl class="review-summary">
            <div class="review-row">
              <dt>{{ t('hotelDetail.reserve.hotelLabel') }}</dt>
              <dd>{{ hotelName }}</dd>
            </div>
            <div class="review-row">
              <dt>{{ t('hotelDetail.reserve.roomLabel') }}</dt>
              <dd>{{ humanize(selectedRoom.type) }} · {{ t('hotelDetail.roomMeta', { number: selectedRoom.number, capacity: selectedRoom.capacity }) }}</dd>
            </div>
            <div class="review-row">
              <dt>{{ t('hotelDetail.reserve.datesLabel') }}</dt>
              <dd>{{ checkIn }} → {{ checkOut }}</dd>
            </div>
            <div class="review-row">
              <dt>{{ t('hotelDetail.guests') }}</dt>
              <dd>{{ guestCount }}</dd>
            </div>
          </dl>

          <label class="field">
            <span class="field-label">{{ t('hotelDetail.reserve.specialRequestLabel') }}</span>
            <textarea
              v-model="specialRequest"
              rows="2"
              :placeholder="t('hotelDetail.reserve.specialRequestPlaceholder')"
            />
          </label>

          <div class="price-breakdown">
            <div class="price-row">
              <span>{{ t('hotelDetail.reserve.priceBreakdownNights', { nights, rate: currency.format(selectedRoom.pricePerNight) }) }}</span>
              <span>{{ currency.format(total) }}</span>
            </div>
            <div class="price-row price-row-total">
              <span>{{ t('hotelDetail.reserve.total') }}</span>
              <span>{{ currency.format(total) }}</span>
            </div>
          </div>

          <p v-if="reserveError" class="error">{{ reserveError }}</p>

          <button type="button" class="brass-btn" :disabled="reserveLoading" @click="reserve">
            {{ reserveLoading ? t('hotelDetail.reserve.reserving') : t('hotelDetail.reserve.reserveButton') }}
          </button>
          <p class="no-charge-note">{{ t('hotelDetail.reserve.noChargeNote') }}</p>
        </div>

        <!-- Step: reserved -->
        <div v-else-if="step === 'done'" class="step done-step">
          <span class="ink-stamp">{{ t('home.stepReservedStamp') }}</span>
          <h2>{{ t('hotelDetail.reserve.successTitle') }}</h2>
          <p class="subtitle">{{ t('hotelDetail.reserve.successBody', { hotelName }) }}</p>
          <div class="done-actions">
            <router-link to="/bookings" class="brass-btn" @click="emit('close')">
              {{ t('hotelDetail.reserve.viewBookings') }}
            </router-link>
            <button type="button" class="link-btn" @click="emit('close')">
              {{ t('auth.close') }}
            </button>
          </div>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.overlay {
  position: fixed;
  inset: 0;
  background: rgba(10, 6, 4, 0.72);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1.5rem;
  z-index: 200;
  animation: fade-in 0.2s ease both;
}
@keyframes fade-in {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.panel {
  position: relative;
  width: 100%;
  max-width: 440px;
  max-height: calc(100vh - 3rem);
  overflow-y: auto;
  background: var(--ink-raised);
  border: 1px solid var(--border);
  border-radius: 6px;
  box-shadow: 0 30px 60px -30px rgba(0, 0, 0, 0.65), 0 2px 0 rgba(0, 0, 0, 0.25);
  padding: 2.5rem 2rem 2rem;
  animation: settle 0.3s cubic-bezier(0.16, 1, 0.3, 1) both;
}
@keyframes settle {
  from {
    opacity: 0;
    transform: translateY(10px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}
@media (prefers-reduced-motion: reduce) {
  .overlay,
  .panel {
    animation: none;
  }
}

.close-btn,
.back-btn {
  position: absolute;
  top: 1.1rem;
  background: none;
  border: none;
  color: var(--text-dim);
  cursor: pointer;
  padding: 0.35rem;
  line-height: 0;
  transition: color 0.15s ease;
}
.close-btn {
  right: 1.1rem;
}
.back-btn {
  left: 1.1rem;
}
.close-btn:hover,
.back-btn:hover {
  color: var(--brass-bright);
}

.step {
  display: flex;
  flex-direction: column;
  gap: 1.4rem;
}

.folio-no {
  font-family: var(--mono);
  font-size: 0.68rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-dim);
  margin: 0 1.6rem -0.6rem 0;
}

.step h2 {
  font-family: var(--serif);
  font-size: clamp(1.3rem, 3vw, 1.55rem);
  font-weight: 600;
  color: var(--text-h);
  padding-right: 1.5rem;
}

.subtitle {
  font-family: var(--mono);
  font-size: 0.82rem;
  color: var(--text-dim);
  margin-top: -0.6rem;
}

/* ---------- room picker ---------- */
.room-options {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  max-height: 40vh;
  overflow-y: auto;
}
.room-option {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 3px;
  padding: 0.85rem 1rem;
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.room-option:hover {
  border-color: var(--brass-dim);
}
.room-option.selected {
  border-color: var(--brass);
  background: rgba(201, 154, 75, 0.08);
}
.room-option-main {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.room-option-main strong {
  font-family: var(--serif);
  font-size: 1rem;
  color: var(--text-h);
}
.room-option-meta {
  font-family: var(--mono);
  font-size: 0.72rem;
  color: var(--text-dim);
}
.room-option-price {
  font-family: var(--mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.92rem;
  color: var(--text-h);
  text-align: right;
  white-space: nowrap;
}
.per-stay {
  display: block;
  font-size: 0.64rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-dim);
}

.modal-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 1rem;
  padding-top: 1rem;
  border-top: 1px solid var(--border);
}
.footer-total {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--text-h);
}
.footer-total-empty {
  color: var(--text-dim);
}

/* ---------- review ---------- */
.review-summary {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin: 0;
}
.review-row {
  display: flex;
  justify-content: space-between;
  gap: 1rem;
  font-size: 0.88rem;
}
.review-row dt {
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-dim);
}
.review-row dd {
  margin: 0;
  color: var(--text-h);
  text-align: right;
}

.field {
  display: flex;
  flex-direction: column;
  gap: 0.4rem;
}
.field-label {
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-dim);
}
.field textarea {
  border: none;
  border-bottom: 1px solid var(--border);
  background: transparent;
  font-family: var(--serif);
  font-style: italic;
  font-size: 0.95rem;
  color: var(--text-h);
  padding: 0.35rem 0;
  resize: vertical;
  transition: border-color 0.15s ease;
}
.field textarea::placeholder {
  color: var(--text-dim);
  opacity: 0.6;
}
.field textarea:focus-visible {
  outline: none;
  border-bottom-color: var(--brass);
}

.price-breakdown {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  padding-top: 0.9rem;
  border-top: 1px solid var(--border);
}
.price-row {
  display: flex;
  justify-content: space-between;
  font-family: var(--mono);
  font-size: 0.82rem;
  color: var(--text-dim);
}
.price-row-total {
  font-size: 0.9rem;
  color: var(--text-h);
  font-weight: 600;
}

.error {
  font-family: var(--mono);
  font-size: 0.8rem;
  color: var(--stamp-bright);
  margin: 0;
}

.brass-btn {
  font-family: var(--mono);
  font-size: 0.78rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  color: var(--ink);
  background: var(--brass);
  border: none;
  border-radius: 3px;
  padding: 0.7rem 1rem;
  cursor: pointer;
  text-align: center;
  text-decoration: none;
  display: inline-block;
  transition: background 0.15s ease;
}
.brass-btn:hover:not(:disabled) {
  background: var(--brass-bright);
}
.brass-btn:disabled {
  background: var(--brass-dim);
  cursor: not-allowed;
}

.no-charge-note {
  font-family: var(--mono);
  font-size: 0.72rem;
  color: var(--text-dim);
  text-align: center;
  margin: 0;
}

/* ---------- done ---------- */
.done-step {
  align-items: center;
  text-align: center;
  padding: 1rem 0;
}
.done-step .ink-stamp {
  font-family: var(--mono);
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--stamp-bright);
  border: 2px solid var(--stamp-bright);
  border-radius: 3px;
  padding: 0.4rem 0.75rem;
  transform: rotate(-2deg);
  opacity: 0.92;
}
.done-step h2 {
  padding-right: 0;
}
.done-actions {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  margin-top: 0.5rem;
}
.link-btn {
  font-family: var(--mono);
  font-size: 0.78rem;
  color: var(--text-dim);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}
.link-btn:hover {
  color: var(--brass-bright);
}

@media (max-width: 480px) {
  .panel {
    padding: 2.25rem 1.25rem 1.5rem;
  }
}
</style>
