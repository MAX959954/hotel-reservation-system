<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { loadStripe } from '@stripe/stripe-js'
import type {
  PaymentRequest as StripePaymentRequest,
  Stripe,
  StripeElements,
  StripePaymentElement,
} from '@stripe/stripe-js'
import { ArrowLeft, Banknote, Bitcoin, CircleCheck, CreditCard, Landmark, Loader2, X } from 'lucide-vue-next'
import { paymentsApi } from '@/api/payments'
import { apiErrorMessage } from '@/api/http'
import { useCurrencyStore } from '@/stores/currency'
import type { BookingResponse } from '@/types/booking'
import type { PaymentMethod, PaymentResponse } from '@/types/payment'

const props = defineProps<{ open: boolean; booking: BookingResponse | null }>()
const emit = defineEmits<{ close: []; paid: [PaymentResponse] }>()

const currency = useCurrencyStore()
const { t } = useI18n()

// Loaded once for the page's lifetime, not per modal open — Stripe's own recommendation,
// since it fetches and caches Stripe.js itself under the hood.
const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY ?? '')

type Step = 'method' | 'card' | 'receipt'
const step = ref<Step>('method')
const method = ref<PaymentMethod | null>(null)
const submitting = ref(false)
const error = ref('')
const receipt = ref<PaymentResponse | null>(null)

// Bank transfer / cash / crypto skip Stripe entirely — see PaymentServiceImpl.GATEWAY_METHODS.
const OFFLINE_METHODS = computed<{ value: PaymentMethod; label: string; icon: typeof Landmark }[]>(() => [
  { value: 'BANK_TRANSFER', label: t('payment.bankTransfer'), icon: Landmark },
  { value: 'CASH', label: t('payment.cashOnArrival'), icon: Banknote },
  { value: 'CRYPTO', label: t('payment.crypto'), icon: Bitcoin },
])

const CARD_METHODS = computed<{ value: PaymentMethod; label: string; icon: typeof CreditCard }[]>(() => [
  { value: 'CREDIT_CARD', label: t('payment.creditCard'), icon: CreditCard },
  { value: 'DEBIT_CARD', label: t('payment.debitCard'), icon: CreditCard },
])

const METHOD_LABELS = computed<Record<PaymentMethod, string>>(() => ({
  CREDIT_CARD: t('payment.creditCard'),
  DEBIT_CARD: t('payment.debitCard'),
  GOOGLE_PAY: t('payment.googlePay'),
  BANK_TRANSFER: t('payment.bankTransfer'),
  CASH: t('payment.cashOnArrival'),
  CRYPTO: t('payment.crypto'),
}))

// --- Stripe wiring -----------------------------------------------------------------
// Matches the app's ink/champagne palette (see style.css) so the embedded iframe
// doesn't look like a foreign widget dropped into the modal.
const STRIPE_APPEARANCE = {
  theme: 'night' as const,
  variables: {
    colorPrimary: '#d8b778',
    colorBackground: '#121215',
    colorText: '#f4f1ec',
    colorTextSecondary: 'rgba(244, 241, 236, 0.62)',
    colorDanger: '#fda4af',
    fontFamily: 'Inter, ui-sans-serif, system-ui, sans-serif',
    borderRadius: '10px',
    spacingUnit: '4px',
  },
  rules: {
    '.Input': { border: '1px solid rgba(244, 241, 236, 0.12)', boxShadow: 'none' },
    '.Input:focus': { border: '1px solid #d8b778', boxShadow: 'none' },
    '.Label': {
      fontSize: '11px',
      textTransform: 'uppercase' as const,
      letterSpacing: '0.12em',
      color: 'rgba(244, 241, 236, 0.62)',
    },
  },
}

let stripe: Stripe | null = null
let elements: StripeElements | null = null
let paymentElement: StripePaymentElement | null = null
const paymentElementHost = ref<HTMLDivElement | null>(null)
const paymentId = ref<number | null>(null)

let paymentRequest: StripePaymentRequest | null = null
const prButtonHost = ref<HTMLDivElement | null>(null)
const googlePayReady = ref(false)

async function ensureStripe(): Promise<Stripe> {
  if (!stripe) stripe = await stripePromise
  if (!stripe) throw new Error('Stripe failed to load')
  return stripe
}

/** Mounts a real Google Pay button — only if the browser can actually satisfy it (Chrome
 *  with a payment method saved to the Google account). No polyfill, no fake fallback:
 *  same as Airbnb/Booking, the option just doesn't appear when it can't be honoured. */
async function setupGooglePay() {
  if (!props.booking) return
  try {
    const s = await ensureStripe()
    paymentRequest = s.paymentRequest({
      country: 'US',
      currency: 'eur',
      total: { label: props.booking.hotelName, amount: Math.round(props.booking.totalPrice * 100) },
      requestPayerName: false,
      requestPayerEmail: false,
    })

    const canPay = await paymentRequest.canMakePayment()
    googlePayReady.value = !!canPay
    if (!canPay) {
      // Not a bug — Stripe/Chrome themselves decided this browser/device can't do it
      // (no Google account signed in, or none of its saved cards support web payments).
      // Logged rather than surfaced in the UI since "no eligible wallet" isn't an error
      // the guest did anything wrong to cause; the other methods are still all there.
      console.info('[PaymentModal] Google Pay not offered: canMakePayment() resolved falsy.')
      return
    }

    await nextTick()
    const prElements = s.elements()
    const prButton = prElements.create('paymentRequestButton', {
      paymentRequest,
      style: { paymentRequestButton: { type: 'default', theme: 'dark', height: '48px' } },
    })
    if (prButtonHost.value) prButton.mount(prButtonHost.value)

    // Fires once the guest completes the native Google Pay sheet. We only create the
    // Stripe PaymentIntent at this point — not on modal open — so browsing the payment
    // methods without paying never leaves an orphaned PENDING row behind.
    paymentRequest.on('paymentmethod', async (ev) => {
      try {
        const intent = await paymentsApi.createIntent({
          bookingId: props.booking!.id,
          method: 'GOOGLE_PAY',
          currency: 'EUR',
        })
        const s2 = await ensureStripe()
        const { paymentIntent, error: confirmError } = await s2.confirmCardPayment(
          intent.clientSecret,
          { payment_method: ev.paymentMethod.id },
          { handleActions: false },
        )
        if (confirmError) {
          ev.complete('fail')
          error.value = confirmError.message ?? t('payment.processError')
          return
        }
        ev.complete('success')
        if (paymentIntent?.status === 'requires_action') {
          const { error: actionError } = await s2.confirmCardPayment(intent.clientSecret)
          if (actionError) {
            error.value = actionError.message ?? t('payment.processError')
            return
          }
        }
        receipt.value = await paymentsApi.confirm(intent.paymentId)
        step.value = 'receipt'
      } catch (e) {
        ev.complete('fail')
        error.value = apiErrorMessage(e, t('payment.processError'))
      }
    })
  } catch (e) {
    // This one IS worth surfacing loudly — unlike a plain "not eligible" result, reaching
    // here means Stripe.js itself failed to load or threw, which usually means a
    // misconfigured VITE_STRIPE_PUBLISHABLE_KEY rather than a browser capability gap.
    console.error('[PaymentModal] Google Pay setup failed:', e)
    googlePayReady.value = false
  }
}

async function startCardPayment(m: PaymentMethod) {
  if (!props.booking) return
  submitting.value = true
  error.value = ''
  try {
    const intent = await paymentsApi.createIntent({ bookingId: props.booking.id, method: m, currency: 'EUR' })
    paymentId.value = intent.paymentId
    const s = await ensureStripe()
    elements = s.elements({ clientSecret: intent.clientSecret, appearance: STRIPE_APPEARANCE })
    paymentElement = elements.create('payment')
    step.value = 'card'
    await nextTick()
    if (paymentElementHost.value) paymentElement.mount(paymentElementHost.value)
  } catch (e) {
    error.value = apiErrorMessage(e, t('payment.processError'))
  } finally {
    submitting.value = false
  }
}

async function submitCard() {
  if (!elements || !paymentId.value || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    const s = await ensureStripe()
    // if_required: most test cards succeed without leaving the page; only a card that
    // actually needs 3DS/redirect-based authentication takes the guest anywhere.
    const { error: confirmError } = await s.confirmPayment({ elements, redirect: 'if_required' })
    if (confirmError) {
      error.value = confirmError.message ?? t('payment.processError')
      return
    }
    // Re-verified server-side against Stripe itself — see PaymentServiceImpl.confirm —
    // rather than trusting this client-side resolution as proof of payment.
    receipt.value = await paymentsApi.confirm(paymentId.value)
    step.value = 'receipt'
  } catch (e) {
    error.value = apiErrorMessage(e, t('payment.processError'))
  } finally {
    submitting.value = false
  }
}

async function submitOffline(m: PaymentMethod) {
  if (!props.booking || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    receipt.value = await paymentsApi.pay({ bookingId: props.booking.id, method: m, currency: 'EUR' })
    step.value = 'receipt'
  } catch (e) {
    error.value = apiErrorMessage(e, t('payment.processError'))
  } finally {
    submitting.value = false
  }
}

function pickMethod(m: PaymentMethod) {
  method.value = m
  error.value = ''
  if (m === 'CREDIT_CARD' || m === 'DEBIT_CARD') {
    startCardPayment(m)
  } else {
    submitOffline(m)
  }
}

function done() {
  if (receipt.value) emit('paid', receipt.value)
  emit('close')
}

function backToMethod() {
  error.value = ''
  step.value = 'method'
  teardownCardPayment()
}

function teardownCardPayment() {
  paymentElement?.destroy()
  paymentElement = null
  elements = null
  paymentId.value = null
}

function teardownGooglePay() {
  paymentRequest = null
  googlePayReady.value = false
}

watch(
  () => props.open,
  (isOpen) => {
    document.body.style.overflow = isOpen ? 'hidden' : ''
    teardownCardPayment()
    teardownGooglePay()
    if (isOpen) {
      step.value = 'method'
      method.value = null
      error.value = ''
      receipt.value = null
      setupGooglePay()
    }
  },
)

onBeforeUnmount(() => {
  teardownCardPayment()
  teardownGooglePay()
})
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
        v-if="open && booking"
        class="fixed inset-0 z-50 bg-ink/70 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto"
        role="dialog"
        aria-modal="true"
        aria-labelledby="payment-modal-title"
        @click.self="emit('close')"
        @keydown.esc="emit('close')"
      >
        <div
          class="w-full max-w-lg rounded-[1.75rem] bg-ink-2/90 backdrop-blur-2xl border border-hairline p-6 md:p-8 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)] my-8"
        >
          <div class="flex items-start justify-between gap-4 mb-6">
            <div class="flex items-center gap-3">
              <button
                v-if="step === 'card'"
                type="button"
                class="text-bone-dim hover:text-bone transition-colors"
                aria-label="Back"
                @click="backToMethod"
              >
                <ArrowLeft class="w-4 h-4" aria-hidden="true" />
              </button>
              <h2 id="payment-modal-title" class="font-display text-2xl text-bone">
                <template v-if="step === 'receipt'">{{ $t('payment.received') }}</template>
                <template v-else>{{ $t('payment.payFor', { hotel: booking.hotelName }) }}</template>
              </h2>
            </div>
            <button type="button" class="text-bone-dim hover:text-bone transition-colors" aria-label="Close" @click="emit('close')">
              <X class="w-5 h-5" aria-hidden="true" />
            </button>
          </div>

          <!-- Method picker -->
          <div v-if="step === 'method'" class="flex flex-col gap-4">
            <p class="text-sm font-light text-bone-dim">
              {{ $t('payment.roomNumber', { number: booking.roomNumber }) }} ·
              <span class="text-bone">{{ currency.format(booking.totalPrice) }}</span>
              <span v-if="currency.estimate(booking.totalPrice)" class="text-bone-dim">
                ({{ currency.estimate(booking.totalPrice) }})
              </span>
            </p>

            <div class="flex flex-col gap-2">
              <button
                v-for="m in CARD_METHODS"
                :key="m.value"
                type="button"
                :disabled="submitting"
                class="flex items-center gap-3 px-4 py-3 rounded-[1.1rem] bg-bone/5 border border-hairline hover:border-champagne-dim hover:bg-bone/8 transition-colors text-left disabled:opacity-50"
                @click="pickMethod(m.value)"
              >
                <span class="w-9 h-9 rounded-full bg-bone/5 border border-hairline flex items-center justify-center shrink-0">
                  <Loader2 v-if="submitting && method === m.value" class="w-4 h-4 text-champagne animate-spin" aria-hidden="true" />
                  <component :is="m.icon" v-else class="w-4 h-4 text-champagne" aria-hidden="true" />
                </span>
                <span class="text-sm text-bone">{{ m.label }}</span>
              </button>

              <!-- Real Stripe-mounted Google Pay button — hidden entirely when the
                   browser can't actually satisfy it, same as it would be on Airbnb. -->
              <div v-if="googlePayReady" ref="prButtonHost" class="rounded-[1.1rem] overflow-hidden" />

              <button
                v-for="m in OFFLINE_METHODS"
                :key="m.value"
                type="button"
                :disabled="submitting"
                class="flex items-center gap-3 px-4 py-3 rounded-[1.1rem] bg-bone/5 border border-hairline hover:border-champagne-dim hover:bg-bone/8 transition-colors text-left disabled:opacity-50"
                @click="pickMethod(m.value)"
              >
                <span class="w-9 h-9 rounded-full bg-bone/5 border border-hairline flex items-center justify-center shrink-0">
                  <Loader2 v-if="submitting && method === m.value" class="w-4 h-4 text-champagne animate-spin" aria-hidden="true" />
                  <component :is="m.icon" v-else class="w-4 h-4 text-champagne" aria-hidden="true" />
                </span>
                <span class="text-sm text-bone">{{ m.label }}</span>
              </button>
            </div>

            <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>
          </div>

          <!-- Card entry — a real Stripe Payment Element, not hand-rolled inputs. Card
               data goes straight to Stripe from inside its iframe; this app's own
               frontend and backend never see or transmit it. -->
          <form v-else-if="step === 'card'" class="flex flex-col gap-4" @submit.prevent="submitCard">
            <div ref="paymentElementHost" />

            <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>

            <button
              type="submit"
              :disabled="submitting"
              class="mt-1 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" aria-hidden="true" />
              {{ $t('payment.payAmount', { amount: currency.format(booking.totalPrice) }) }}
            </button>
          </form>

          <!-- Receipt -->
          <div v-else class="flex flex-col items-center gap-4 text-center py-2">
            <span class="w-14 h-14 rounded-full bg-champagne/10 border border-champagne/25 flex items-center justify-center">
              <CircleCheck class="w-7 h-7 text-champagne" aria-hidden="true" />
            </span>
            <div>
              <p class="font-display text-3xl text-bone">{{ currency.format(receipt!.amount) }}</p>
              <p class="text-xs font-light text-bone-dim mt-1">
                {{ METHOD_LABELS[receipt!.method] }}
                <template v-if="receipt!.transactionId"> · {{ receipt!.transactionId }}</template>
              </p>
            </div>
            <p class="text-sm font-light text-bone-dim">
              {{ booking.hotelName }} · {{ $t('payment.roomNumber', { number: booking.roomNumber }) }}
            </p>
            <button
              type="button"
              class="mt-2 w-full flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors"
              @click="done"
            >
              {{ $t('payment.done') }}
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
