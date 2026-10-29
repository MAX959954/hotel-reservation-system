<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { CheckCircle2, Loader2, X } from 'lucide-vue-next'
import DatePicker from './DatePicker.vue'
import NumberStepper from './NumberStepper.vue'
import { bookingsApi } from '@/api/bookings'
import { apiErrorMessage } from '@/api/http'
import { useCurrencyStore } from '@/stores/currency'
import type { RoomResponse } from '@/types/room'
import { roomTypeLabel } from '@/types/room'
import type { BookingResponse } from '@/types/booking'
import {
  CHECK_IN_TIME,
  CHECK_OUT_TIME,
  addDaysIso,
  nightsBetween,
  todayIso,
  toLocalDateTime,
} from '@/lib/dates'

const props = defineProps<{
  open: boolean
  room: RoomResponse | null
  hotelName: string
  initialCheckIn?: string
  initialCheckOut?: string
  initialGuests?: number
}>()

const emit = defineEmits<{ close: []; booked: [booking: BookingResponse] }>()

const currency = useCurrencyStore()
const { t } = useI18n()

const checkIn = ref(todayIso())
const checkOut = ref(addDaysIso(todayIso(), 2))
const guestCount = ref(1)
const specialRequest = ref('')

const busy = ref(false)
const error = ref('')
const created = ref<BookingResponse | null>(null)

watch(
  () => props.open,
  (open) => {
    if (!open) return
    created.value = null
    error.value = ''
    // Both dates are @Future on the server, so today would be rejected — start tomorrow.
    checkIn.value = props.initialCheckIn || addDaysIso(todayIso(), 1)
    checkOut.value = props.initialCheckOut || addDaysIso(todayIso(), 3)
    guestCount.value = props.initialGuests || 1
    specialRequest.value = ''
  },
)

const nights = computed(() => nightsBetween(checkIn.value, checkOut.value))
const total = computed(() => (props.room ? nights.value * props.room.pricePerNight : 0))

const capacityError = computed(() => {
  if (!props.room) return ''
  if (guestCount.value < 1) return t('bookingModal.errorAtLeastOneGuest')
  if (guestCount.value > props.room.capacity)
    return t('bookingModal.errorCapacity', { capacity: props.room.capacity })
  return ''
})

const dateError = computed(() => {
  if (!checkIn.value || !checkOut.value) return t('bookingModal.errorPickDates')
  if (checkIn.value <= todayIso()) return t('bookingModal.errorFutureCheckIn')
  if (nights.value < 1) return t('bookingModal.errorCheckOutAfter')
  return ''
})

const valid = computed(() => !capacityError.value && !dateError.value && !!props.room)

async function submit() {
  if (!valid.value || !props.room || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const booking = await bookingsApi.create({
      roomId: props.room.id,
      checkIn: toLocalDateTime(checkIn.value, CHECK_IN_TIME),
      checkOut: toLocalDateTime(checkOut.value, CHECK_OUT_TIME),
      guestCount: guestCount.value,
      ...(specialRequest.value.trim() ? { specialRequest: specialRequest.value.trim() } : {}),
    })
    created.value = booking
    emit('booked', booking)
  } catch (e) {
    error.value = apiErrorMessage(e, t('bookingModal.submitError'))
  } finally {
    busy.value = false
  }
}
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
        v-if="open && room"
        class="fixed inset-0 z-50 bg-ink/70 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto"
        role="dialog"
        aria-modal="true"
        aria-labelledby="booking-modal-title"
        @click.self="emit('close')"
        @keydown.esc="emit('close')"
      >
        <div
          class="w-full max-w-lg rounded-[1.75rem] bg-ink-2/90 backdrop-blur-2xl border border-hairline p-6 md:p-8 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)] my-8"
        >
          <div class="flex items-start justify-between gap-4 mb-6">
            <div>
              <h2 id="booking-modal-title" class="font-display text-2xl text-bone">
                {{ created ? $t('bookingModal.titleReceived') : $t('bookingModal.titleReserve') }}
              </h2>
              <p class="text-xs font-light text-bone-dim mt-1">
                {{ $t('bookingModal.roomLine', { type: roomTypeLabel(room.type), number: room.number, hotel: hotelName }) }}
              </p>
            </div>
            <button
              type="button"
              class="text-bone-dim hover:text-bone transition-colors"
              :aria-label="$t('common.close')"
              @click="emit('close')"
            >
              <X class="w-5 h-5" />
            </button>
          </div>

          <!-- Confirmation. The server returns PENDING; calling it "confirmed" here would
               tell the guest something the backend has not actually done yet. -->
          <div v-if="created" class="flex flex-col gap-4">
            <div class="flex items-center gap-3 rounded-2xl bg-bone/5 border border-hairline p-4">
              <CheckCircle2 class="w-6 h-6 text-champagne shrink-0" aria-hidden="true" />
              <div>
                <p class="text-sm text-bone">
                  {{ $t('bookingModal.bookingStatus', { id: created.id, status: $t(`bookingStatus.${created.bookingStatus}`) }) }}
                </p>
                <p class="text-xs font-light text-bone-dim mt-0.5">
                  {{ $t('bookingModal.needsConfirmation') }}
                </p>
              </div>
            </div>
            <div class="flex items-center justify-between text-sm">
              <span class="font-light text-bone-dim">{{ $t('bookingModal.total') }}</span>
              <span class="font-display text-2xl text-bone">{{ currency.format(created.totalPrice) }}</span>
            </div>
            <RouterLink
              to="/bookings"
              class="mt-2 text-center rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors"
              @click="emit('close')"
            >
              {{ $t('bookingModal.viewBookings') }}
            </RouterLink>
          </div>

          <form v-else class="flex flex-col gap-4" @submit.prevent="submit">
            <div class="grid grid-cols-2 gap-3">
              <label class="flex flex-col gap-1">
                <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('search.checkInLabel') }}</span>
                <DatePicker v-model="checkIn" :min="addDaysIso(todayIso(), 1)" :aria-label="$t('search.checkInLabel')" />
              </label>
              <label class="flex flex-col gap-1">
                <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('search.checkOutLabel') }}</span>
                <DatePicker v-model="checkOut" :min="addDaysIso(checkIn, 1)" :aria-label="$t('search.checkOutLabel')" />
              </label>
            </div>
            <p v-if="dateError" class="text-xs text-rose-300 -mt-2">{{ dateError }}</p>

            <div class="flex items-center justify-between gap-3">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">
                {{ $t('bookingModal.guestsSleeps', { capacity: room.capacity }) }}
              </span>
              <NumberStepper v-model="guestCount" :min="1" :max="room.capacity" :aria-label="$t('bookingModal.guests')" />
            </div>
            <span v-if="capacityError" class="text-xs text-rose-300 -mt-2">{{ capacityError }}</span>

            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">
                {{ $t('bookingModal.specialRequest') }} <span class="normal-case tracking-normal">{{ $t('bookingModal.optional') }}</span>
              </span>
              <textarea
                v-model="specialRequest"
                rows="2"
                class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 resize-none transition-colors"
              />
            </label>

            <div
              class="flex items-baseline justify-between border-t border-hairline pt-4 mt-2"
              aria-live="polite"
            >
              <span class="text-sm font-light text-bone-dim">
                {{ $t('bookingModal.nights', { n: nights }, nights) }} ×
                {{ currency.format(room.pricePerNight) }}
              </span>
              <span class="text-right">
                <span class="font-display text-3xl text-bone block">{{ currency.format(total) }}</span>
                <span v-if="currency.estimate(total)" class="text-[11px] font-light text-bone-dim">
                  {{ currency.estimate(total) }}
                </span>
              </span>
            </div>

            <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>

            <button
              type="submit"
              :disabled="!valid || busy"
              class="flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="busy" class="w-4 h-4 animate-spin" aria-hidden="true" />
              {{ $t('bookingModal.reserve') }}
            </button>
          </form>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
