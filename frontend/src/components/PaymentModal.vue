<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ArrowLeft, Banknote, Bitcoin, CircleCheck, CreditCard, Landmark, Loader2, X } from 'lucide-vue-next'
import { paymentsApi } from '@/api/payments'
import { apiErrorMessage } from '@/api/http'
import { useCurrencyStore } from '@/stores/currency'
import type { BookingResponse } from '@/types/booking'
import type { PaymentMethod, PaymentResponse } from '@/types/payment'

const props = defineProps<{ open: boolean; booking: BookingResponse | null }>()
const emit = defineEmits<{ close: []; paid: [PaymentResponse] }>()

const currency = useCurrencyStore()

type Step = 'method' | 'card' | 'receipt'
const step = ref<Step>('method')
const method = ref<PaymentMethod | null>(null)
const submitting = ref(false)
const error = ref('')
const receipt = ref<PaymentResponse | null>(null)

const cardName = ref('')
const cardNumber = ref('')
const cardExpiry = ref('')
const cardCvv = ref('')

const METHODS: { value: PaymentMethod; label: string; icon: typeof CreditCard }[] = [
  { value: 'CREDIT_CARD', label: 'Credit card', icon: CreditCard },
  { value: 'DEBIT_CARD', label: 'Debit card', icon: CreditCard },
  { value: 'BANK_TRANSFER', label: 'Bank transfer', icon: Landmark },
  { value: 'CASH', label: 'Cash on arrival', icon: Banknote },
  { value: 'CRYPTO', label: 'Crypto', icon: Bitcoin },
]

const METHOD_LABELS: Record<PaymentMethod, string> = {
  CREDIT_CARD: 'Credit card',
  DEBIT_CARD: 'Debit card',
  BANK_TRANSFER: 'Bank transfer',
  CASH: 'Cash on arrival',
  CRYPTO: 'Crypto',
}

const digitsOnly = computed(() => cardNumber.value.replace(/\D/g, ''))
const cardValid = computed(
  () =>
    cardName.value.trim().length > 1 &&
    digitsOnly.value.length >= 13 &&
    digitsOnly.value.length <= 19 &&
    /^\d{2}\/\d{2}$/.test(cardExpiry.value) &&
    expiryNotPast(cardExpiry.value) &&
    /^\d{3,4}$/.test(cardCvv.value),
)

function expiryNotPast(value: string): boolean {
  const match = /^(\d{2})\/(\d{2})$/.exec(value)
  if (!match) return false
  const month = Number(match[1])
  const year = 2000 + Number(match[2])
  if (month < 1 || month > 12) return false
  const expiry = new Date(year, month, 1) // first day of the month *after* expiry
  return expiry.getTime() > Date.now()
}

function onCardNumberInput(e: Event) {
  const digits = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 19)
  cardNumber.value = digits.replace(/(\d{4})(?=\d)/g, '$1 ')
}

function onExpiryInput(e: Event) {
  const digits = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 4)
  cardExpiry.value = digits.length > 2 ? `${digits.slice(0, 2)}/${digits.slice(2)}` : digits
}

function onCvvInput(e: Event) {
  cardCvv.value = (e.target as HTMLInputElement).value.replace(/\D/g, '').slice(0, 4)
}

function pickMethod(m: PaymentMethod) {
  method.value = m
  error.value = ''
  if (m === 'CREDIT_CARD' || m === 'DEBIT_CARD') {
    step.value = 'card'
  } else {
    submit()
  }
}

/** Never the real card number — just enough to make the receipt feel like a real one. */
function fakeTransactionId(): string | undefined {
  if (method.value !== 'CREDIT_CARD' && method.value !== 'DEBIT_CARD') return undefined
  const last4 = digitsOnly.value.slice(-4)
  return `card_${last4}_${Date.now().toString(36)}`
}

async function submit() {
  if (!props.booking || !method.value || submitting.value) return
  submitting.value = true
  error.value = ''
  try {
    receipt.value = await paymentsApi.pay({
      bookingId: props.booking.id,
      method: method.value,
      currency: 'EUR',
      transactionId: fakeTransactionId(),
    })
    step.value = 'receipt'
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not process that payment.')
  } finally {
    submitting.value = false
  }
}

function submitCard() {
  if (!cardValid.value || submitting.value) return
  submit()
}

function done() {
  if (receipt.value) emit('paid', receipt.value)
  emit('close')
}

function backToMethod() {
  error.value = ''
  step.value = 'method'
}

watch(
  () => props.open,
  (isOpen) => {
    document.body.style.overflow = isOpen ? 'hidden' : ''
    if (isOpen) {
      step.value = 'method'
      method.value = null
      error.value = ''
      receipt.value = null
      cardName.value = ''
      cardNumber.value = ''
      cardExpiry.value = ''
      cardCvv.value = ''
    }
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
        v-if="open && booking"
        class="fixed inset-0 z-50 bg-ink/70 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto"
        role="dialog"
        aria-modal="true"
        aria-labelledby="payment-modal-title"
        @click.self="emit('close')"
        @keydown.esc="emit('close')"
      >
        <div
          class="w-full max-w-md rounded-[1.75rem] bg-ink-2/90 backdrop-blur-2xl border border-hairline p-6 md:p-8 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)] my-8"
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
                <template v-if="step === 'receipt'">Payment received</template>
                <template v-else>Pay for {{ booking.hotelName }}</template>
              </h2>
            </div>
            <button type="button" class="text-bone-dim hover:text-bone transition-colors" aria-label="Close" @click="emit('close')">
              <X class="w-5 h-5" aria-hidden="true" />
            </button>
          </div>

          <!-- Method picker -->
          <div v-if="step === 'method'" class="flex flex-col gap-4">
            <p class="text-sm font-light text-bone-dim">
              Room {{ booking.roomNumber }} ·
              <span class="text-bone">{{ currency.format(booking.totalPrice) }}</span>
              <span v-if="currency.estimate(booking.totalPrice)" class="text-bone-dim">
                ({{ currency.estimate(booking.totalPrice) }})
              </span>
            </p>

            <div class="flex flex-col gap-2">
              <button
                v-for="m in METHODS"
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

          <!-- Card entry -->
          <form v-else-if="step === 'card'" class="flex flex-col gap-4" @submit.prevent="submitCard">
            <!-- Decorative preview only — nothing here is transmitted as raw card data;
                 the server has no card-number field at all, see types/payment.ts. -->
            <div
              class="rounded-[1.25rem] bg-gradient-to-br from-ink-2 via-ink to-ink-2 border border-champagne/20 p-5 flex flex-col justify-between h-36 relative overflow-hidden"
            >
              <div class="absolute -right-8 -top-8 w-32 h-32 rounded-full bg-champagne/10 blur-2xl" aria-hidden="true" />
              <div class="flex items-center justify-between">
                <CreditCard class="w-7 h-7 text-champagne" aria-hidden="true" />
                <span class="text-[11px] uppercase tracking-[0.14em] text-bone-dim">{{ METHOD_LABELS[method!] }}</span>
              </div>
              <div class="flex flex-col gap-1">
                <span class="font-mono text-lg text-bone tracking-[0.15em]">{{ cardNumber || '•••• •••• •••• ••••' }}</span>
                <div class="flex items-center justify-between">
                  <span class="text-xs font-light text-bone-dim uppercase truncate max-w-[60%]">{{ cardName || 'Card holder' }}</span>
                  <span class="text-xs font-light text-bone-dim">{{ cardExpiry || 'MM/YY' }}</span>
                </div>
              </div>
            </div>

            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Cardholder name</span>
              <input
                v-model="cardName"
                type="text"
                autocomplete="cc-name"
                required
                class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
              />
            </label>

            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Card number</span>
              <input
                :value="cardNumber"
                type="text"
                inputmode="numeric"
                autocomplete="cc-number"
                placeholder="0000 0000 0000 0000"
                required
                class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone placeholder:text-bone-dim/50 font-light py-1 transition-colors"
                @input="onCardNumberInput"
              />
            </label>

            <div class="grid grid-cols-2 gap-3">
              <label class="flex flex-col gap-1">
                <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Expiry</span>
                <input
                  :value="cardExpiry"
                  type="text"
                  inputmode="numeric"
                  autocomplete="cc-exp"
                  placeholder="MM/YY"
                  required
                  class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone placeholder:text-bone-dim/50 font-light py-1 transition-colors"
                  @input="onExpiryInput"
                />
              </label>
              <label class="flex flex-col gap-1">
                <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">CVV</span>
                <input
                  :value="cardCvv"
                  type="password"
                  inputmode="numeric"
                  autocomplete="cc-csc"
                  placeholder="123"
                  required
                  class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone placeholder:text-bone-dim/50 font-light py-1 transition-colors"
                  @input="onCvvInput"
                />
              </label>
            </div>

            <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>

            <button
              type="submit"
              :disabled="!cardValid || submitting"
              class="mt-1 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" aria-hidden="true" />
              Pay {{ currency.format(booking.totalPrice) }}
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
              {{ booking.hotelName }} · Room {{ booking.roomNumber }}
            </p>
            <button
              type="button"
              class="mt-2 w-full flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors"
              @click="done"
            >
              Done
            </button>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
