<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Loader2, Mail, X } from 'lucide-vue-next'
import { authApi } from '@/api/auth'
import { apiErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useAuthModalStore } from '@/stores/authModal'
import { todayIso } from '@/lib/dates'

const router = useRouter()
const auth = useAuthStore()
const modal = useAuthModalStore()

const busy = ref(false)
const error = ref('')

const email = ref('')
const codeDigits = ref<string[]>(['', '', '', '', '', ''])
const codeInputs = ref<HTMLInputElement[]>([])

const firstName = ref('')
const lastName = ref('')
const dateOfBirth = ref('')
const password = ref('')

const resendIn = ref(0)
let resendTimer: ReturnType<typeof setInterval> | null = null

const code = computed(() => codeDigits.value.join(''))

const emailValid = computed(() => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.value.trim()))

/** Mirrors the server's @MinAge(18) so the user is not told "no" only after a round-trip. */
const ageValid = computed(() => {
  if (!dateOfBirth.value) return false
  const dob = new Date(`${dateOfBirth.value}T00:00:00`)
  if (Number.isNaN(dob.getTime())) return false
  const cutoff = new Date()
  cutoff.setFullYear(cutoff.getFullYear() - 18)
  return dob <= cutoff
})

const passwordStrength = computed(() => {
  const v = password.value
  let score = 0
  if (v.length >= 8) score++
  if (v.length >= 12) score++
  if (/[A-Z]/.test(v) && /[a-z]/.test(v)) score++
  if (/\d/.test(v)) score++
  if (/[^A-Za-z0-9]/.test(v)) score++
  return Math.min(score, 4)
})

const strengthLabel = computed(
  () => ['Too short', 'Weak', 'Fair', 'Good', 'Strong'][passwordStrength.value],
)

const registerValid = computed(
  () =>
    firstName.value.trim() &&
    lastName.value.trim() &&
    ageValid.value &&
    password.value.length >= 8,
)

watch(
  () => modal.open,
  (open) => {
    if (open) {
      error.value = ''
      email.value = ''
      codeDigits.value = ['', '', '', '', '', '']
      firstName.value = ''
      lastName.value = ''
      dateOfBirth.value = ''
      password.value = ''
      document.body.style.overflow = 'hidden'
    } else {
      document.body.style.overflow = ''
      stopResendTimer()
    }
  },
)

function startResendTimer() {
  resendIn.value = 30
  stopResendTimer()
  resendTimer = setInterval(() => {
    resendIn.value -= 1
    if (resendIn.value <= 0) stopResendTimer()
  }, 1000)
}

function stopResendTimer() {
  if (resendTimer) clearInterval(resendTimer)
  resendTimer = null
}

onUnmounted(() => {
  stopResendTimer()
  document.body.style.overflow = ''
})

async function submitEmail() {
  if (!emailValid.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await authApi.requestOtp(email.value.trim())
    modal.identifier = email.value.trim()
    modal.step = 'code'
    startResendTimer()
    await nextTick()
    codeInputs.value[0]?.focus()
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not send the code. Try again.')
  } finally {
    busy.value = false
  }
}

async function resend() {
  if (resendIn.value > 0 || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await authApi.requestOtp(modal.identifier)
    startResendTimer()
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

function setCodeInput(el: unknown, index: number) {
  if (el instanceof HTMLInputElement) codeInputs.value[index] = el
}

function onDigit(index: number, event: Event) {
  const input = event.target as HTMLInputElement
  const value = input.value.replace(/\D/g, '')

  if (value.length > 1) {
    // Paste of a full code: spread it across the boxes instead of stuffing one.
    const chars = value.slice(0, 6).split('')
    chars.forEach((c, i) => {
      if (index + i < 6) codeDigits.value[index + i] = c
    })
    const next = Math.min(index + chars.length, 5)
    codeInputs.value[next]?.focus()
  } else {
    codeDigits.value[index] = value
    if (value && index < 5) codeInputs.value[index + 1]?.focus()
  }

  input.value = codeDigits.value[index] ?? ''
  if (code.value.length === 6) submitCode()
}

function onDigitKeydown(index: number, event: KeyboardEvent) {
  if (event.key === 'Backspace' && !codeDigits.value[index] && index > 0) {
    codeInputs.value[index - 1]?.focus()
  }
}

async function submitCode() {
  if (code.value.length !== 6 || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const res = await authApi.verifyOtp(modal.identifier, code.value)
    if (!res.newAccount && res.auth) {
      finish(res.auth)
      return
    }
    modal.verificationTicket = res.verificationTicket ?? null
    modal.step = 'register'
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    codeDigits.value = ['', '', '', '', '', '']
    // "Verification expired — start again." means the ticket is gone; step 2 can no
    // longer succeed, so send the user back rather than letting them retype a dead code.
    if (/expired/i.test(message) && /start again/i.test(message)) {
      modal.step = 'identifier'
    } else {
      await nextTick()
      codeInputs.value[0]?.focus()
    }
  } finally {
    busy.value = false
  }
}

async function submitRegistration() {
  if (!registerValid.value || !modal.verificationTicket || busy.value) return
  busy.value = true
  error.value = ''
  try {
    const res = await authApi.completeRegistration({
      verificationTicket: modal.verificationTicket,
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      dateOfBirth: dateOfBirth.value,
      password: password.value,
    })
    finish(res)
  } catch (e) {
    error.value = apiErrorMessage(e)
  } finally {
    busy.value = false
  }
}

function finish(session: Parameters<typeof auth.setSession>[0]) {
  auth.setSession(session)
  const intended = modal.intendedRoute
  modal.close(true)
  if (intended) router.push(intended)
}

function close() {
  modal.close(false)
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
        v-if="modal.open"
        class="fixed inset-0 z-50 bg-ink/70 backdrop-blur-sm flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="auth-modal-title"
        @click.self="close"
        @keydown.esc="close"
      >
        <div
          class="w-full max-w-md rounded-[1.75rem] bg-ink-2/90 backdrop-blur-2xl border border-hairline p-6 md:p-8 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)]"
        >
          <div class="flex items-start justify-between gap-4 mb-6">
            <div class="flex items-center gap-3">
              <button
                v-if="modal.step !== 'identifier'"
                type="button"
                class="text-bone-dim hover:text-bone transition-colors"
                aria-label="Back"
                @click="modal.step = modal.step === 'register' ? 'code' : 'identifier'"
              >
                <ArrowLeft class="w-4 h-4" />
              </button>
              <h2 id="auth-modal-title" class="font-display text-2xl text-bone">
                <template v-if="modal.step === 'identifier'">Sign in to Folio</template>
                <template v-else-if="modal.step === 'code'">Check your inbox</template>
                <template v-else>Finish your account</template>
              </h2>
            </div>
            <button
              type="button"
              class="text-bone-dim hover:text-bone transition-colors"
              aria-label="Close"
              @click="close"
            >
              <X class="w-5 h-5" />
            </button>
          </div>

          <!-- Step 1 — email only: the server validates this field with @Email. -->
          <form v-if="modal.step === 'identifier'" class="flex flex-col gap-4" @submit.prevent="submitEmail">
            <p class="text-sm font-light text-bone-dim">
              We'll email you a six-digit code. No password needed.
            </p>
            <label class="flex items-center gap-3 border-b border-hairline pb-2 focus-within:border-champagne transition-colors">
              <Mail class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
              <span class="sr-only">Email address</span>
              <input
                v-model="email"
                type="email"
                required
                autocomplete="email"
                placeholder="you@example.com"
                class="bg-transparent outline-none text-sm text-bone placeholder:text-bone-dim/60 w-full font-light py-1"
              />
            </label>
            <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>
            <button
              type="submit"
              :disabled="!emailValid || busy"
              class="mt-2 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="busy" class="w-4 h-4 animate-spin" aria-hidden="true" />
              Send code
            </button>
          </form>

          <!-- Step 2 — six digits, paste-aware. -->
          <form v-else-if="modal.step === 'code'" class="flex flex-col gap-4" @submit.prevent="submitCode">
            <p class="text-sm font-light text-bone-dim">
              Sent to <span class="text-bone">{{ modal.identifier }}</span>. It expires in 10 minutes.
            </p>
            <div class="flex gap-2 justify-between">
              <input
                v-for="(_, i) in codeDigits"
                :key="i"
                :ref="(el) => setCodeInput(el, i)"
                :value="codeDigits[i]"
                type="text"
                inputmode="numeric"
                maxlength="6"
                :aria-label="`Digit ${i + 1}`"
                class="w-12 h-14 text-center rounded-xl bg-bone/5 border border-hairline text-xl text-bone font-display outline-none focus:border-champagne transition-colors"
                @input="onDigit(i, $event)"
                @keydown="onDigitKeydown(i, $event)"
              />
            </div>
            <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>
            <div class="flex items-center justify-between text-xs font-light text-bone-dim">
              <button
                type="button"
                :disabled="resendIn > 0 || busy"
                class="hover:text-bone transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
                @click="resend"
              >
                {{ resendIn > 0 ? `Resend in ${resendIn}s` : 'Resend code' }}
              </button>
              <Loader2 v-if="busy" class="w-4 h-4 animate-spin text-champagne" aria-hidden="true" />
            </div>
          </form>

          <!-- Step 3 — new accounts only. -->
          <form v-else class="flex flex-col gap-4" @submit.prevent="submitRegistration">
            <div class="grid grid-cols-2 gap-3">
              <label class="flex flex-col gap-1">
                <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">First name</span>
                <input
                  v-model="firstName"
                  type="text"
                  required
                  autocomplete="given-name"
                  class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
                />
              </label>
              <label class="flex flex-col gap-1">
                <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Last name</span>
                <input
                  v-model="lastName"
                  type="text"
                  required
                  autocomplete="family-name"
                  class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
                />
              </label>
            </div>

            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Date of birth</span>
              <input
                v-model="dateOfBirth"
                type="date"
                required
                :max="todayIso()"
                class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
              />
              <span v-if="dateOfBirth && !ageValid" class="text-xs text-rose-300">
                You must be at least 18 years old to book with us.
              </span>
            </label>

            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Password</span>
              <input
                v-model="password"
                type="password"
                required
                minlength="8"
                autocomplete="new-password"
                class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
              />
              <div class="flex items-center gap-2 mt-1">
                <div class="flex-1 h-1 rounded-full bg-bone/10 overflow-hidden">
                  <div
                    class="h-full rounded-full bg-champagne transition-all duration-300"
                    :style="{ width: `${(passwordStrength / 4) * 100}%` }"
                  />
                </div>
                <span class="text-[11px] font-light text-bone-dim w-16 text-right">{{ strengthLabel }}</span>
              </div>
            </label>

            <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>

            <button
              type="submit"
              :disabled="!registerValid || busy"
              class="mt-2 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="busy" class="w-4 h-4 animate-spin" aria-hidden="true" />
              Create account
            </button>
          </form>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
