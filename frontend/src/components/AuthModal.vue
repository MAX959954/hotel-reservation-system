<script setup lang="ts">
import { computed, nextTick, onUnmounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Loader2, Lock, Mail, X } from 'lucide-vue-next'
import DatePicker from './DatePicker.vue'
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
/** Set only for the specific "an account already exists" error, so the form can offer a
 *  direct way to switch to sign-in instead of just printing the sentence at the user. */
const suggestSignIn = ref(false)

const email = ref('')
const password = ref('')

const firstName = ref('')
const lastName = ref('')
const dateOfBirth = ref('')
const confirmPassword = ref('')

const codeDigits = ref<string[]>(['', '', '', '', '', ''])
const codeInputs = ref<HTMLInputElement[]>([])

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

/** Oldest-guest-on-record to 18-years-ago — a plain prev/next-month calendar would take
 *  dozens of clicks to reach a real birth year, so DatePicker gets year/month selects here. */
const dobYearRange: [number, number] = (() => {
  const currentYear = new Date().getFullYear()
  return [currentYear - 100, currentYear - 18]
})()

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
const strengthLabel = computed(() => ['Too short', 'Weak', 'Fair', 'Good', 'Strong'][passwordStrength.value])

const passwordsMatch = computed(() => confirmPassword.value.length > 0 && confirmPassword.value === password.value)

const signinValid = computed(() => emailValid.value && password.value.length > 0)
const registerValid = computed(
  () =>
    emailValid.value &&
    firstName.value.trim() &&
    lastName.value.trim() &&
    ageValid.value &&
    password.value.length >= 8 &&
    passwordsMatch.value,
)

watch(
  () => modal.open,
  (open) => {
    if (open) {
      error.value = ''
      suggestSignIn.value = false
      email.value = ''
      password.value = ''
      confirmPassword.value = ''
      firstName.value = ''
      lastName.value = ''
      dateOfBirth.value = ''
      codeDigits.value = ['', '', '', '', '', '']
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

/** Sign-in: password is checked here; a code is only ever sent once it's correct. */
async function submitSignin() {
  if (!signinValid.value || busy.value) return
  busy.value = true
  error.value = ''
  try {
    await authApi.login(email.value.trim(), password.value)
    await advanceToCode()
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not sign in. Try again.')
  } finally {
    busy.value = false
  }
}

/** Register: everything is collected up front; the code step that follows only confirms
 *  the email address, it doesn't ask for any of this again. */
async function submitRegister() {
  if (!registerValid.value || busy.value) return
  busy.value = true
  error.value = ''
  suggestSignIn.value = false
  try {
    await authApi.requestOtp(email.value.trim())
    await advanceToCode()
  } catch (e) {
    const message = apiErrorMessage(e, 'Could not send the code. Try again.')
    error.value = message
    suggestSignIn.value = /sign in with your password instead/i.test(message)
  } finally {
    busy.value = false
  }
}

async function advanceToCode() {
  modal.identifier = email.value.trim()
  modal.step = 'code'
  startResendTimer()
  await nextTick()
  codeInputs.value[0]?.focus()
}

function switchToSignIn() {
  modal.intent = 'signin'
  error.value = ''
  suggestSignIn.value = false
  password.value = ''
}

async function resend() {
  if (resendIn.value > 0 || busy.value) return
  busy.value = true
  error.value = ''
  try {
    if (modal.intent === 'signin') {
      await authApi.login(email.value.trim(), password.value)
    } else {
      await authApi.requestOtp(modal.identifier)
    }
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

    if (modal.intent === 'signin') {
      // login() only ever sends a code for an existing, password-verified account, so
      // this is always the "already a member" branch — but check res.auth rather than
      // assume, since trusting a response shape you haven't looked at is how the last
      // few bugs in this app happened.
      if (res.auth) {
        finish(res.auth)
        return
      }
      throw new Error('Unexpected response completing sign-in.')
    }

    // Registration: the details were already collected in the form step, so this call
    // needs no further input from the user.
    if (!res.newAccount || !res.verificationTicket) {
      throw new Error('Unexpected response completing registration.')
    }
    const auth2 = await authApi.completeRegistration({
      verificationTicket: res.verificationTicket,
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      dateOfBirth: dateOfBirth.value,
      password: password.value,
    })
    finish(auth2)
  } catch (e) {
    const message = apiErrorMessage(e)
    error.value = message
    codeDigits.value = ['', '', '', '', '', '']
    // "Verification expired — start again." means the ticket behind this code is gone;
    // the form step can no longer complete anything, so send the user all the way back.
    if (/expired/i.test(message) && /start again/i.test(message)) {
      modal.step = 'form'
    } else {
      await nextTick()
      codeInputs.value[0]?.focus()
    }
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

function backToForm() {
  error.value = ''
  suggestSignIn.value = false
  modal.step = 'form'
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
        class="fixed inset-0 z-50 bg-ink/70 backdrop-blur-sm flex items-center justify-center p-4 overflow-y-auto"
        role="dialog"
        aria-modal="true"
        aria-labelledby="auth-modal-title"
        @click.self="close"
        @keydown.esc="close"
      >
        <div
          class="w-full max-w-md rounded-[1.75rem] bg-ink-2/90 backdrop-blur-2xl border border-hairline p-6 md:p-8 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)] my-8"
        >
          <div class="flex items-start justify-between gap-4 mb-6">
            <div class="flex items-center gap-3">
              <button
                v-if="modal.step === 'code'"
                type="button"
                class="text-bone-dim hover:text-bone transition-colors"
                aria-label="Back"
                @click="backToForm"
              >
                <ArrowLeft class="w-4 h-4" />
              </button>
              <h2 id="auth-modal-title" class="font-display text-2xl text-bone">
                <template v-if="modal.step === 'form'">{{
                  modal.intent === 'register' ? 'Create your account' : 'Sign in to Folio'
                }}</template>
                <template v-else>Check your inbox</template>
              </h2>
            </div>
            <button type="button" class="text-bone-dim hover:text-bone transition-colors" aria-label="Close" @click="close">
              <X class="w-5 h-5" />
            </button>
          </div>

          <!-- Sign in: email + password. A code follows once the password checks out. -->
          <form
            v-if="modal.step === 'form' && modal.intent === 'signin'"
            class="flex flex-col gap-4"
            @submit.prevent="submitSignin"
          >
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
            <label class="flex items-center gap-3 border-b border-hairline pb-2 focus-within:border-champagne transition-colors">
              <Lock class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
              <span class="sr-only">Password</span>
              <input
                v-model="password"
                type="password"
                required
                autocomplete="current-password"
                placeholder="Password"
                class="bg-transparent outline-none text-sm text-bone placeholder:text-bone-dim/60 w-full font-light py-1"
              />
            </label>

            <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>

            <button
              type="submit"
              :disabled="!signinValid || busy"
              class="mt-2 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="busy" class="w-4 h-4 animate-spin" aria-hidden="true" />
              Continue
            </button>

            <p class="text-xs font-light text-bone-dim text-center">
              New to Folio?
              <button type="button" class="text-champagne hover:text-champagne-bright transition-colors" @click="modal.intent = 'register'">
                Create an account
              </button>
            </p>
          </form>

          <!-- Register: name, email, date of birth, password, confirm password. -->
          <form
            v-else-if="modal.step === 'form'"
            class="flex flex-col gap-4"
            @submit.prevent="submitRegister"
          >
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
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Email</span>
              <input
                v-model="email"
                type="email"
                required
                autocomplete="email"
                class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
              />
            </label>

            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Date of birth</span>
              <DatePicker
                v-model="dateOfBirth"
                :max="todayIso()"
                :year-range="dobYearRange"
                aria-label="Date of birth"
                class="border-b border-hairline focus-within:border-champagne pb-1 transition-colors"
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

            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Confirm password</span>
              <input
                v-model="confirmPassword"
                type="password"
                required
                autocomplete="new-password"
                class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
              />
              <span v-if="confirmPassword && !passwordsMatch" class="text-xs text-rose-300">
                Passwords don't match.
              </span>
            </label>

            <p v-if="error" class="text-xs text-rose-300">
              {{ error }}
              <button
                v-if="suggestSignIn"
                type="button"
                class="text-champagne hover:text-champagne-bright transition-colors underline underline-offset-2"
                @click="switchToSignIn"
              >
                Sign in instead
              </button>
            </p>

            <button
              type="submit"
              :disabled="!registerValid || busy"
              class="mt-2 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="busy" class="w-4 h-4 animate-spin" aria-hidden="true" />
              Continue
            </button>

            <p class="text-xs font-light text-bone-dim text-center">
              Already have an account?
              <button type="button" class="text-champagne hover:text-champagne-bright transition-colors" @click="switchToSignIn">
                Sign in
              </button>
            </p>
          </form>

          <!-- Code — common to both flows. -->
          <form v-else class="flex flex-col gap-4" @submit.prevent="submitCode">
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
            <p class="text-xs font-light text-bone-dim/70">
              Didn't get it? Check your spam folder — delivery can occasionally take a minute.
            </p>
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
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
