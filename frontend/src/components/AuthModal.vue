<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthModalStore } from '../stores/authModal'
import { useAuthStore } from '../stores/auth'
import { authApi } from '../api/auth'

const { t } = useI18n()
const router = useRouter()
const modal = useAuthModalStore()
const auth = useAuthStore()

const panelRef = ref<HTMLElement | null>(null)
const firstFieldRef = ref<HTMLElement | null>(null)

// ---------- step 1: identify ----------
const identifierInput = ref('')
const identifyLoading = ref(false)
const identifyError = ref('')

const googleClientId = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined
const googleBtnHost = ref<HTMLElement | null>(null)
let googleScriptPromise: Promise<void> | null = null

function loadGoogleScript() {
  if (googleScriptPromise) return googleScriptPromise
  googleScriptPromise = new Promise((resolve, reject) => {
    if (document.getElementById('google-identity-script')) return resolve()
    const script = document.createElement('script')
    script.id = 'google-identity-script'
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    script.defer = true
    script.onload = () => resolve()
    script.onerror = () => reject(new Error('Failed to load Google script'))
    document.head.appendChild(script)
  })
  return googleScriptPromise
}

async function renderGoogleButton() {
  if (!googleClientId || !googleBtnHost.value) return
  try {
    await loadGoogleScript()
    const g = (window as any).google
    if (!g?.accounts?.id) return
    g.accounts.id.initialize({
      client_id: googleClientId,
      callback: onGoogleCredential,
    })
    googleBtnHost.value.innerHTML = ''
    g.accounts.id.renderButton(googleBtnHost.value, {
      theme: 'filled_black',
      shape: 'pill',
      size: 'large',
      text: 'continue_with',
      width: 320,
    })
  } catch {
    // Google script failed to load (offline, blocked) — the rest of the modal still works.
  }
}

async function onGoogleCredential(response: { credential: string }) {
  identifyError.value = ''
  identifyLoading.value = true
  try {
    const res = await authApi.google(response.credential)
    auth.setSession(res)
    finishAndClose()
  } catch (err: any) {
    identifyError.value = err.response?.data?.message ?? t('auth.identify.errorFallback')
  } finally {
    identifyLoading.value = false
  }
}

async function onIdentifySubmit() {
  identifyError.value = ''
  identifyLoading.value = true
  try {
    await authApi.requestOtp(identifierInput.value.trim())
    modal.goToCode(identifierInput.value.trim())
  } catch (err: any) {
    identifyError.value = err.response?.data?.message ?? t('auth.identify.errorFallback')
  } finally {
    identifyLoading.value = false
  }
}

// ---------- step 2: code ----------
const digits = ref(['', '', '', '', '', ''])
const digitRefs = ref<(HTMLInputElement | null)[]>([])
const codeLoading = ref(false)
const codeError = ref('')
const resendCooldown = ref(30)
let resendTimer: ReturnType<typeof setInterval> | null = null

function startResendCooldown() {
  resendCooldown.value = 30
  if (resendTimer) clearInterval(resendTimer)
  resendTimer = setInterval(() => {
    if (resendCooldown.value > 0) resendCooldown.value -= 1
    else if (resendTimer) clearInterval(resendTimer)
  }, 1000)
}

function onDigitInput(index: number, event: Event) {
  const input = event.target as HTMLInputElement
  const value = input.value.replace(/\D/g, '')
  digits.value[index] = value.slice(-1)
  if (value && index < 5) {
    digitRefs.value[index + 1]?.focus()
  }
  if (digits.value.every((d) => d !== '')) {
    submitCode()
  }
}

function onDigitKeydown(index: number, event: KeyboardEvent) {
  if (event.key === 'Backspace' && !digits.value[index] && index > 0) {
    digitRefs.value[index - 1]?.focus()
  }
}

function onDigitPaste(event: ClipboardEvent) {
  const pasted = event.clipboardData?.getData('text').replace(/\D/g, '').slice(0, 6)
  if (!pasted) return
  event.preventDefault()
  for (let i = 0; i < 6; i++) {
    digits.value[i] = pasted[i] ?? ''
  }
  const lastFilled = Math.min(pasted.length, 6) - 1
  digitRefs.value[Math.max(lastFilled, 0)]?.focus()
  if (pasted.length === 6) submitCode()
}

async function submitCode() {
  const code = digits.value.join('')
  if (code.length !== 6) return
  codeError.value = ''
  codeLoading.value = true
  try {
    const res = await authApi.verifyOtp(modal.identifier, code)
    if (res.newAccount && res.verificationTicket) {
      modal.goToRegister(res.verificationTicket)
    } else if (res.auth) {
      auth.setSession(res.auth)
      finishAndClose()
    }
  } catch (err: any) {
    codeError.value = err.response?.data?.message ?? t('auth.code.errorFallback')
    digits.value = ['', '', '', '', '', '']
    digitRefs.value[0]?.focus()
  } finally {
    codeLoading.value = false
  }
}

async function onResend() {
  if (resendCooldown.value > 0) return
  codeError.value = ''
  try {
    await authApi.requestOtp(modal.identifier)
    startResendCooldown()
  } catch (err: any) {
    codeError.value = err.response?.data?.message ?? t('auth.code.errorFallback')
  }
}

function onBackToIdentify() {
  modal.backToIdentify()
  digits.value = ['', '', '', '', '', '']
  codeError.value = ''
}

// ---------- step 3: register ----------
const firstName = ref('')
const lastName = ref('')
const dateOfBirth = ref('')
const password = ref('')
const showPassword = ref(false)
const registerLoading = ref(false)
const registerError = ref('')

const strength = computed(() => {
  const pw = password.value
  if (!pw) return { level: 0, label: '' }
  let score = 0
  if (pw.length >= 8) score++
  if (pw.length >= 12) score++
  if (/[a-z]/.test(pw) && /[A-Z]/.test(pw)) score++
  if (/\d/.test(pw)) score++
  if (/[^A-Za-z0-9]/.test(pw)) score++

  let level: number
  if (score <= 1) level = 1
  else if (score === 2) level = 2
  else if (score <= 4) level = 3
  else level = 4

  const labels = ['', t('auth.register.strengthWeak'), t('auth.register.strengthFair'), t('auth.register.strengthGood'), t('auth.register.strengthStrong')]
  return { level, label: labels[level] }
})

async function onRegisterSubmit() {
  registerError.value = ''
  registerLoading.value = true
  try {
    const res = await authApi.completeRegistration({
      verificationTicket: modal.verificationTicket!,
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      dateOfBirth: dateOfBirth.value,
      password: password.value,
    })
    auth.setSession(res)
    finishAndClose()
  } catch (err: any) {
    registerError.value = err.response?.data?.message ?? t('auth.register.errorFallback')
  } finally {
    registerLoading.value = false
  }
}

// ---------- shared ----------
function finishAndClose() {
  const redirect = modal.redirectTo
  modal.close()
  if (redirect) router.push(redirect)
}

function onOverlayClick() {
  modal.close()
}

function onKeydown(event: KeyboardEvent) {
  if (event.key === 'Escape') {
    modal.close()
    return
  }
  if (event.key === 'Tab' && panelRef.value) {
    const focusables = panelRef.value.querySelectorAll<HTMLElement>(
      'button:not(:disabled), input:not(:disabled), a[href]',
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

watch(
  () => modal.step,
  async () => {
    codeError.value = ''
    identifyError.value = ''
    registerError.value = ''
    if (modal.step === 'code') {
      digits.value = ['', '', '', '', '', '']
      startResendCooldown()
    }
    await nextTick()
    firstFieldRef.value?.focus()
    if (modal.step === 'identify') {
      identifierInput.value = ''
      await nextTick()
      renderGoogleButton()
    }
  },
)

watch(
  () => modal.isOpen,
  async (open) => {
    if (open) {
      identifierInput.value = ''
      firstName.value = ''
      lastName.value = ''
      dateOfBirth.value = ''
      password.value = ''
      await nextTick()
      firstFieldRef.value?.focus()
      if (modal.step === 'identify') renderGoogleButton()
    }
  },
)

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})
onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
  if (resendTimer) clearInterval(resendTimer)
})
</script>

<template>
  <Teleport to="body">
    <div v-if="modal.isOpen" class="overlay" @mousedown.self="onOverlayClick">
      <div
        ref="panelRef"
        class="panel"
        role="dialog"
        aria-modal="true"
        :aria-label="t('auth.modalLabel')"
      >
        <button type="button" class="close-btn" :aria-label="t('auth.close')" @click="modal.close()">
          <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
            <path d="M1 1l14 14M15 1L1 15" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
          </svg>
        </button>

        <!-- Step 1: identify -->
        <form v-if="modal.step === 'identify'" class="step" @submit.prevent="onIdentifySubmit">
          <p class="folio-no">{{ t('auth.folioMark') }}</p>
          <h2>{{ t('auth.identify.title') }}</h2>
          <p class="subtitle">{{ t('auth.identify.subtitle') }}</p>

          <label class="field">
            <span class="field-label">{{ t('auth.identify.emailLabel') }}</span>
            <input
              ref="firstFieldRef"
              v-model="identifierInput"
              type="email"
              required
              autocomplete="email"
              placeholder="you@example.com"
            />
          </label>

          <p v-if="identifyError" class="error">{{ identifyError }}</p>

          <button type="submit" class="brass-btn" :disabled="identifyLoading">
            {{ identifyLoading ? t('auth.identify.sending') : t('auth.identify.continueButton') }}
          </button>

          <div class="divider"><span>{{ t('auth.identify.or') }}</span></div>

          <div ref="googleBtnHost" class="google-btn-host" v-show="googleClientId"></div>

          <button type="button" class="social-btn" disabled>
            <svg viewBox="0 0 384 512" width="16" height="16" aria-hidden="true">
              <path
                fill="currentColor"
                d="M318.7 268.7c-.2-36.7 16.4-64.4 50-84.8-18.8-26.9-47.2-41.7-84.7-44.6-35.5-2.8-74.3 20.7-88.5 20.7-15 0-49.4-19.7-76.4-19.7C63.3 141.2 4 184.8 4 273.5q0 39.3 14.4 81.2c12.8 36.7 59 126.7 107.2 125.2 25.2-.6 43-17.9 75.8-17.9 31.8 0 48.3 17.9 76.4 17.9 48.6-.7 90.4-82.5 102.6-119.3-65.2-30.7-61.7-90-61.7-91.9zm-56.6-164.2c27.3-32.4 24.8-61.9 24-72.5-24.1 1.4-52 16.4-67.9 34.9-17.5 19.8-27.8 44.3-25.6 71.9 26.1 2 49.9-11.4 69.5-34.3z"
              />
            </svg>
            <span>{{ t('auth.identify.appleButton') }}</span>
            <span class="coming-soon">{{ t('auth.identify.comingSoon') }}</span>
          </button>
        </form>

        <!-- Step 2: code -->
        <form v-else-if="modal.step === 'code'" class="step" @submit.prevent="submitCode">
          <button type="button" class="back-btn" :aria-label="t('auth.back')" @click="onBackToIdentify">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
              <path d="M10 2L4 8l6 6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <p class="folio-no">{{ t('auth.folioMark') }}</p>
          <h2>{{ t('auth.code.title') }}</h2>
          <p class="subtitle">{{ t('auth.code.subtitle', { identifier: modal.identifier }) }}</p>

          <div class="code-cells">
            <input
              v-for="(digit, i) in digits"
              :key="i"
              :ref="(el) => (digitRefs[i] = el as HTMLInputElement)"
              v-model="digits[i]"
              class="code-cell"
              type="text"
              inputmode="numeric"
              pattern="\d*"
              maxlength="1"
              autocomplete="one-time-code"
              :disabled="codeLoading"
              @input="onDigitInput(i, $event)"
              @keydown="onDigitKeydown(i, $event)"
              @paste="onDigitPaste"
            />
          </div>

          <p v-if="codeError" class="error">{{ codeError }}</p>

          <button type="submit" class="brass-btn" :disabled="codeLoading">
            {{ codeLoading ? t('auth.code.verifying') : t('auth.code.verifyButton') }}
          </button>

          <p class="resend">
            <button type="button" class="link-btn" :disabled="resendCooldown > 0" @click="onResend">
              {{ resendCooldown > 0 ? t('auth.code.resendIn', { seconds: resendCooldown }) : t('auth.code.resend') }}
            </button>
          </p>
        </form>

        <!-- Step 3: register -->
        <form v-else class="step" @submit.prevent="onRegisterSubmit">
          <button type="button" class="back-btn" :aria-label="t('auth.back')" @click="modal.backToIdentify()">
            <svg viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
              <path d="M10 2L4 8l6 6" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round" />
            </svg>
          </button>
          <p class="folio-no">{{ t('auth.folioMark') }}</p>
          <h2>{{ t('auth.register.title') }}</h2>
          <p class="subtitle">{{ t('auth.register.subtitle') }}</p>

          <div class="name-row">
            <label class="field">
              <span class="field-label">{{ t('auth.register.firstName') }}</span>
              <input ref="firstFieldRef" v-model="firstName" type="text" required autocomplete="given-name" />
            </label>
            <label class="field">
              <span class="field-label">{{ t('auth.register.lastName') }}</span>
              <input v-model="lastName" type="text" required autocomplete="family-name" />
            </label>
          </div>

          <label class="field">
            <span class="field-label">{{ t('auth.register.dateOfBirth') }}</span>
            <input v-model="dateOfBirth" type="date" required autocomplete="bday" />
          </label>

          <label class="field">
            <span class="field-label">
              {{ t('auth.register.password') }}
              <button type="button" class="show-toggle" @click="showPassword = !showPassword">
                {{ showPassword ? t('auth.register.hide') : t('auth.register.show') }}
              </button>
            </span>
            <input
              v-model="password"
              :type="showPassword ? 'text' : 'password'"
              required
              minlength="8"
              autocomplete="new-password"
            />
          </label>

          <div v-if="password" class="strength" :class="`strength-${strength.level}`">
            <span class="strength-bar">
              <span v-for="n in 4" :key="n" class="strength-seg" :class="{ filled: n <= strength.level }" />
            </span>
            <span class="strength-label">{{ t('auth.register.strengthPrefix') }} {{ strength.label }}</span>
          </div>

          <p v-if="registerError" class="error">{{ registerError }}</p>

          <p class="terms">{{ t('auth.register.terms') }}</p>

          <button type="submit" class="brass-btn" :disabled="registerLoading">
            {{ registerLoading ? t('auth.register.creating') : t('auth.register.createButton') }}
          </button>
        </form>
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
  max-width: 400px;
  max-height: calc(100vh - 3rem);
  overflow-y: auto;
  background: var(--ink-raised);
  border: 1px solid var(--border);
  border-radius: 6px;
  /* Reuses the system's one shadow token (see DESIGN.md "One Shadow Rule") — the modal
     is, like the hero ledger, an object resting above the desk rather than a card. */
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
  font-size: clamp(1.4rem, 3vw, 1.7rem);
  font-weight: 600;
  color: var(--text-h);
  padding-right: 1.5rem;
}

.subtitle {
  font-family: var(--serif);
  font-size: 0.95rem;
  color: var(--text-dim);
  margin-top: -0.6rem;
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
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.field input {
  border: none;
  border-bottom: 1px solid var(--border);
  background: transparent;
  font-family: var(--serif);
  font-style: italic;
  font-size: 1.05rem;
  color: var(--text-h);
  padding: 0.35rem 0;
  transition: border-color 0.15s ease;
  width: 100%;
}
.field input::placeholder {
  color: var(--text-dim);
  opacity: 0.6;
}
.field input:focus-visible {
  outline: none;
  border-bottom-color: var(--brass);
}
.field input[type='date'] {
  color-scheme: dark;
}

.name-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 1rem;
}

.show-toggle {
  font-family: var(--mono);
  font-size: 0.68rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--brass-bright);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
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
  transition: background 0.15s ease;
}
.brass-btn:hover:not(:disabled) {
  background: var(--brass-bright);
}
.brass-btn:disabled {
  background: var(--brass-dim);
  cursor: not-allowed;
}

.error {
  font-family: var(--mono);
  font-size: 0.8rem;
  color: var(--stamp-bright);
  margin: -0.8rem 0 0;
}

.divider {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin: -0.4rem 0;
  color: var(--text-dim);
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
}
.divider::before,
.divider::after {
  content: '';
  flex: 1;
  height: 1px;
  background: var(--border);
}

.google-btn-host {
  display: flex;
  justify-content: center;
}

.social-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 0.6rem;
  width: 100%;
  font-family: var(--mono);
  font-size: 0.78rem;
  letter-spacing: 0.03em;
  color: var(--text-dim);
  background: transparent;
  border: 1px solid var(--border);
  border-radius: 3px;
  padding: 0.65rem 1rem;
  cursor: not-allowed;
}
.coming-soon {
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--text-dim);
  opacity: 0.75;
}

.code-cells {
  display: flex;
  gap: 0.6rem;
  justify-content: center;
}
.code-cell {
  width: 2.4rem;
  height: 3rem;
  text-align: center;
  border: none;
  border-bottom: 1px solid var(--border);
  background: transparent;
  font-family: var(--serif);
  font-style: italic;
  font-size: 1.5rem;
  color: var(--text-h);
  transition: border-color 0.15s ease;
}
.code-cell:focus-visible {
  outline: none;
  border-bottom-color: var(--brass);
}

.resend {
  text-align: center;
  margin: -0.6rem 0 0;
}
.link-btn {
  font-family: var(--mono);
  font-size: 0.78rem;
  color: var(--brass-bright);
  background: none;
  border: none;
  cursor: pointer;
  padding: 0;
}
.link-btn:disabled {
  color: var(--text-dim);
  cursor: default;
}

.strength {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  margin-top: -0.8rem;
}
.strength-bar {
  display: flex;
  gap: 0.25rem;
}
.strength-seg {
  width: 1.4rem;
  height: 3px;
  background: var(--border);
  border-radius: 2px;
}
.strength-seg.filled {
  background: var(--brass-dim);
}
.strength-3 .strength-seg.filled,
.strength-4 .strength-seg.filled {
  background: var(--brass);
}
.strength-4 .strength-seg.filled {
  background: var(--brass-bright);
}
.strength-label {
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.04em;
  color: var(--text-dim);
}

.terms {
  font-family: var(--mono);
  font-size: 0.7rem;
  line-height: 1.5;
  color: var(--text-dim);
  margin: -0.6rem 0 0;
}

@media (max-width: 480px) {
  .panel {
    padding: 2.25rem 1.25rem 1.5rem;
  }
  .code-cell {
    width: 2.1rem;
  }
  .name-row {
    grid-template-columns: 1fr;
  }
}
</style>
