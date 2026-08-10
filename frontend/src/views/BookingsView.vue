<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { CreditCard, Loader2, RotateCw } from 'lucide-vue-next'
import Navbar from '@/components/Navbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import PaymentModal from '@/components/PaymentModal.vue'
import { bookingsApi } from '@/api/bookings'
import { paymentsApi } from '@/api/payments'
import { apiErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useConfirmModalStore } from '@/stores/confirmModal'
import { useCurrencyStore } from '@/stores/currency'
import { formatDateRange } from '@/lib/dates'
import { bookingStatusLabel, type BookingResponse, type BookingStatus } from '@/types/booking'
import { paymentMethodLabel, type PaymentResponse } from '@/types/payment'

const { t } = useI18n()
const auth = useAuthStore()
const confirmModal = useConfirmModalStore()
const currency = useCurrencyStore()

const bookings = ref<BookingResponse[]>([])
/** Keyed by booking id — only populated for bookings that could plausibly have a payment
 *  (see `load`), so a missing key means "not checked", not "definitely unpaid". */
const payments = ref<Record<number, PaymentResponse>>({})
const loading = ref(true)
const error = ref('')
const cancellingId = ref<number | null>(null)

const payingBooking = ref<BookingResponse | null>(null)
const paymentModalOpen = ref(false)

const STATUS_CLASSES: Record<BookingStatus, string> = {
  PENDING: 'text-amber-300/90 bg-amber-300/10 border-amber-300/20',
  CONFIRMED: 'text-champagne bg-champagne/10 border-champagne/25',
  CHECKED_IN: 'text-sky-300/90 bg-sky-300/10 border-sky-300/20',
  COMPLETED: 'text-bone-dim bg-bone/5 border-hairline',
  CANCELLED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  NO_SHOW: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  PAYMENT_FAILED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
}

/** The server refuses to cancel exactly these two, so the button would only ever 400. */
function canCancel(status: BookingStatus) {
  return status !== 'COMPLETED' && status !== 'CANCELLED'
}

// Matches PaymentServiceImpl.pay(): it 400s for anything but a CONFIRMED booking, so a
// "Pay now" button on any other status would only ever fail. A PENDING payment record
// (a Stripe attempt the guest opened but never finished — closed the modal, declined
// card, ...) does not count as "already paid": the backend clears it out and lets a
// fresh attempt through, so the button has to stay available for that case too.
function canPay(booking: BookingResponse) {
  const existing = payments.value[booking.id]
  return booking.bookingStatus === 'CONFIRMED' && (!existing || existing.status !== 'COMPLETED')
}

async function load() {
  if (!auth.userId) return
  loading.value = true
  error.value = ''
  try {
    bookings.value = await bookingsApi.getByUser(auth.userId)
    // Only bookings that ever reach CONFIRMED can have a payment — asking for the rest
    // would just be a guaranteed-empty round trip for every PENDING/CANCELLED booking.
    const candidates = bookings.value.filter((b) =>
      ['CONFIRMED', 'CHECKED_IN', 'COMPLETED'].includes(b.bookingStatus),
    )
    const results = await Promise.all(candidates.map((b) => paymentsApi.getByBooking(b.id)))
    const next: Record<number, PaymentResponse> = {}
    candidates.forEach((b, i) => {
      const payment = results[i]
      if (payment) next[b.id] = payment
    })
    payments.value = next
  } catch (e) {
    error.value = apiErrorMessage(e, t('bookings.loadError'))
  } finally {
    loading.value = false
  }
}

function openPay(booking: BookingResponse) {
  payingBooking.value = booking
  paymentModalOpen.value = true
}

function onPaid(receipt: PaymentResponse) {
  payments.value = { ...payments.value, [receipt.bookingId]: receipt }
}

async function cancel(booking: BookingResponse) {
  const confirmed = await confirmModal.ask({
    title: t('bookings.cancelModalTitle'),
    message: t('bookings.cancelModalMessage', { hotel: booking.hotelName }),
    confirmLabel: t('bookings.cancelModalConfirm'),
    cancelLabel: t('bookings.cancelModalKeep'),
    danger: true,
  })
  if (!confirmed) return
  cancellingId.value = booking.id
  error.value = ''
  try {
    await bookingsApi.cancel(booking.id)
    // Refetch rather than patching in place: cancelling also frees the room server-side,
    // and the authoritative status is whatever the server says it is now.
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e, t('bookings.cancelError'))
  } finally {
    cancellingId.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <div class="border-b border-hairline">
      <Navbar />
    </div>

    <main class="flex-1 px-6 md:px-10 py-10 max-w-4xl mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-8">{{ $t('bookings.title') }}</h1>

      <div v-if="loading" class="flex flex-col gap-3">
        <div v-for="i in 3" :key="i" class="h-32 rounded-[1.25rem] bg-ink-2 border border-hairline animate-pulse" />
      </div>

      <div
        v-else-if="error"
        class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4"
      >
        <p class="text-sm text-rose-300">{{ error }}</p>
        <button
          type="button"
          class="flex items-center gap-2 rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors"
          @click="load"
        >
          <RotateCw class="w-4 h-4" aria-hidden="true" />
          {{ $t('bookings.retry') }}
        </button>
      </div>

      <div
        v-else-if="!bookings.length"
        class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4"
      >
        <p class="text-sm font-light text-bone-dim">{{ $t('bookings.empty') }}</p>
        <RouterLink
          to="/"
          class="rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors"
        >
          {{ $t('bookings.findStay') }}
        </RouterLink>
      </div>

      <div v-else class="flex flex-col gap-4">
        <article
          v-for="booking in bookings"
          :key="booking.id"
          class="rounded-[1.25rem] bg-ink-2 border border-hairline p-6 flex flex-wrap items-start justify-between gap-4"
        >
          <div class="min-w-0">
            <div class="flex items-center gap-3 flex-wrap">
              <h2 class="font-display text-2xl text-bone">{{ booking.hotelName }}</h2>
              <span
                class="px-2.5 py-1 rounded-full text-[11px] font-medium border"
                :class="STATUS_CLASSES[booking.bookingStatus]"
              >
                {{ bookingStatusLabel(booking.bookingStatus) }}
              </span>
            </div>
            <p class="text-sm font-light text-bone-dim mt-2">
              {{ $t('payment.roomNumber', { number: booking.roomNumber }) }} ·
              {{ formatDateRange(booking.checkIn, booking.checkOut) }} ·
              {{ booking.guestCount }} {{ booking.guestCount === 1 ? $t('hotels.guest') : $t('hotels.guests') }}
            </p>
            <p v-if="booking.specialRequest" class="text-xs font-light text-bone-dim/80 mt-2 italic">
              “{{ booking.specialRequest }}”
            </p>
          </div>

          <div class="flex flex-col items-end gap-3">
            <p class="text-right">
              <span class="font-display text-2xl text-bone">{{ currency.format(booking.totalPrice) }}</span>
              <span v-if="currency.estimate(booking.totalPrice)" class="block text-[11px] font-light text-bone-dim">
                {{ currency.estimate(booking.totalPrice) }}
              </span>
            </p>

            <span
              v-if="payments[booking.id]?.status === 'COMPLETED'"
              class="flex items-center gap-1.5 px-2.5 py-1 rounded-full text-[11px] font-medium text-champagne bg-champagne/10 border border-champagne/25"
            >
              <CreditCard class="w-3 h-3" aria-hidden="true" />
              {{ $t('bookings.paidVia', { method: paymentMethodLabel(payments[booking.id].method) }) }}
            </span>

            <div class="flex items-center gap-2">
              <button
                v-if="canCancel(booking.bookingStatus)"
                type="button"
                :disabled="cancellingId === booking.id"
                class="flex items-center gap-2 rounded-full border border-hairline px-4 py-2 text-xs font-light text-bone-dim hover:text-bone hover:border-rose-300/40 transition-colors disabled:opacity-50"
                @click="cancel(booking)"
              >
                <Loader2 v-if="cancellingId === booking.id" class="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                {{ $t('bookings.cancel') }}
              </button>
              <button
                v-if="canPay(booking)"
                type="button"
                class="flex items-center gap-2 rounded-full bg-champagne text-ink px-4 py-2 text-xs font-medium hover:bg-champagne-bright transition-colors"
                @click="openPay(booking)"
              >
                {{ $t('bookings.payNow') }}
              </button>
            </div>
          </div>
        </article>
      </div>
    </main>

    <SiteFooter />

    <PaymentModal
      :open="paymentModalOpen"
      :booking="payingBooking"
      @close="paymentModalOpen = false"
      @paid="onPaid"
    />
  </div>
</template>
