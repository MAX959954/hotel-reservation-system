<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { Camera, Check, CircleUser, Coins, KeyRound, Loader2, ShieldCheck, X } from 'lucide-vue-next'
import { accountApi } from '@/api/account'
import { apiErrorMessage, resolveUploadUrl } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useSettingsModalStore } from '@/stores/settingsModal'
import type { SettingsTab } from '@/stores/settingsModal'
import { CURRENCIES, useCurrencyStore } from '@/stores/currency'
import type { CurrencyCode } from '@/stores/currency'
import type { UserProfileResponse } from '@/types/account'

const auth = useAuthStore()

const modal = useSettingsModalStore()
const currency = useCurrencyStore()

const SECTIONS: { id: SettingsTab; label: string; icon: typeof CircleUser }[] = [
  { id: 'personal', label: 'Personal info', icon: CircleUser },
  { id: 'security', label: 'Login & security', icon: ShieldCheck },
  { id: 'currency', label: 'Languages & currency', icon: Coins },
]

const profile = ref<UserProfileResponse | null>(null)
const firstName = ref('')
const lastName = ref('')
const phone = ref('')

const loading = ref(false)
const saving = ref(false)
const error = ref('')
const saved = ref(false)

const avatarInput = ref<HTMLInputElement | null>(null)
const avatarUploading = ref(false)
const avatarError = ref('')
const avatarSrc = computed(() => resolveUploadUrl(profile.value?.avatarUrl))

const currentPassword = ref('')
const newPassword = ref('')
const confirmNewPassword = ref('')
const passwordSaving = ref(false)
const passwordError = ref('')
const passwordSaved = ref(false)
const newPasswordValid = computed(
  () => newPassword.value.length >= 8 && newPassword.value === confirmNewPassword.value,
)

const dirty = computed(
  () =>
    !!profile.value &&
    (firstName.value !== profile.value.firstName ||
      lastName.value !== profile.value.lastName ||
      phone.value !== (profile.value.phone ?? '')),
)

watch(
  () => modal.open,
  async (open) => {
    if (!open) return
    error.value = ''
    saved.value = false
    if (!profile.value) {
      loading.value = true
      try {
        profile.value = await accountApi.getProfile()
        firstName.value = profile.value.firstName
        lastName.value = profile.value.lastName
        phone.value = profile.value.phone ?? ''
        auth.setAvatar(profile.value.avatarUrl)
      } catch (e) {
        error.value = apiErrorMessage(e, 'Could not load your account.')
      } finally {
        loading.value = false
      }
    }
  },
)

async function saveProfile() {
  if (!dirty.value || saving.value) return
  saving.value = true
  error.value = ''
  try {
    profile.value = await accountApi.updateProfile({
      firstName: firstName.value.trim(),
      lastName: lastName.value.trim(),
      phone: phone.value.trim() || undefined,
    })
    saved.value = true
  } catch (e) {
    error.value = apiErrorMessage(e, 'Could not save your changes.')
  } finally {
    saving.value = false
  }
}

function pickAvatar() {
  avatarInput.value?.click()
}

async function onAvatarChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file) return
  avatarUploading.value = true
  avatarError.value = ''
  try {
    profile.value = await accountApi.uploadAvatar(file)
    auth.setAvatar(profile.value.avatarUrl)
  } catch (e) {
    avatarError.value = apiErrorMessage(e, 'Could not upload that image.')
  } finally {
    avatarUploading.value = false
    ;(event.target as HTMLInputElement).value = ''
  }
}

async function savePassword() {
  if (!newPasswordValid.value || passwordSaving.value) return
  passwordSaving.value = true
  passwordError.value = ''
  passwordSaved.value = false
  try {
    await accountApi.changePassword({ currentPassword: currentPassword.value, newPassword: newPassword.value })
    currentPassword.value = ''
    newPassword.value = ''
    confirmNewPassword.value = ''
    passwordSaved.value = true
  } catch (e) {
    passwordError.value = apiErrorMessage(e, 'Could not update your password.')
  } finally {
    passwordSaving.value = false
  }
}

function pickCurrency(code: CurrencyCode) {
  currency.setCode(code)
}

function memberSince(iso: string | undefined): string {
  if (!iso) return ''
  return new Intl.DateTimeFormat('en-GB', { month: 'long', year: 'numeric' }).format(new Date(iso))
}

function formatDate(iso: string | null | undefined): string {
  if (!iso) return 'Not on file'
  return new Intl.DateTimeFormat('en-GB', { day: 'numeric', month: 'long', year: 'numeric' }).format(new Date(iso))
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
        aria-labelledby="settings-modal-title"
        @click.self="modal.close()"
        @keydown.esc="modal.close()"
      >
        <div
          class="w-full max-w-2xl rounded-[1.75rem] bg-ink-2/90 backdrop-blur-2xl border border-hairline shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)] flex flex-col md:flex-row overflow-hidden max-h-[85vh]"
        >
          <!-- Sidebar -->
          <div class="md:w-52 shrink-0 border-b md:border-b-0 md:border-r border-hairline p-4 md:p-5">
            <h2 id="settings-modal-title" class="font-display text-xl text-bone px-2 mb-3">Settings</h2>
            <nav class="flex md:flex-col gap-1 overflow-x-auto md:overflow-visible" role="tablist">
              <button
                v-for="s in SECTIONS"
                :key="s.id"
                type="button"
                role="tab"
                :aria-selected="modal.tab === s.id"
                class="flex items-center gap-2.5 px-3 py-2.5 rounded-lg text-sm font-light whitespace-nowrap transition-colors shrink-0"
                :class="modal.tab === s.id ? 'bg-champagne text-ink' : 'text-bone-dim hover:text-bone hover:bg-bone/5'"
                @click="modal.tab = s.id"
              >
                <component :is="s.icon" class="w-4 h-4" aria-hidden="true" />
                {{ s.label }}
              </button>
            </nav>
          </div>

          <!-- Content -->
          <div class="flex-1 p-6 md:p-8 overflow-y-auto">
            <button
              type="button"
              class="float-right text-bone-dim hover:text-bone transition-colors"
              aria-label="Close"
              @click="modal.close()"
            >
              <X class="w-5 h-5" />
            </button>

            <!-- Personal info -->
            <div v-if="modal.tab === 'personal'" class="flex flex-col gap-4 max-w-sm">
              <h3 class="font-display text-2xl text-bone mb-1">Personal info</h3>

              <div v-if="loading" class="flex flex-col gap-3">
                <div class="h-4 w-2/3 rounded animate-pulse bg-bone/5" />
                <div class="h-4 w-1/2 rounded animate-pulse bg-bone/5" />
              </div>

              <template v-else-if="profile">
                <div class="flex items-center gap-4">
                  <button
                    type="button"
                    class="relative w-16 h-16 rounded-full shrink-0 group"
                    aria-label="Change profile photo"
                    @click="pickAvatar"
                  >
                    <img v-if="avatarSrc" :src="avatarSrc" alt="" class="w-16 h-16 rounded-full object-cover" />
                    <span
                      v-else
                      class="w-16 h-16 rounded-full bg-champagne text-ink flex items-center justify-center text-xl font-medium"
                    >
                      {{ profile.firstName.charAt(0).toUpperCase() }}
                    </span>
                    <span
                      class="absolute inset-0 rounded-full bg-ink/60 opacity-0 group-hover:opacity-100 flex items-center justify-center transition-opacity"
                    >
                      <Loader2 v-if="avatarUploading" class="w-4 h-4 text-bone animate-spin" aria-hidden="true" />
                      <Camera v-else class="w-4 h-4 text-bone" aria-hidden="true" />
                    </span>
                  </button>
                  <div class="flex flex-col gap-1">
                    <button
                      type="button"
                      class="text-sm text-champagne hover:text-champagne-bright transition-colors w-fit"
                      @click="pickAvatar"
                    >
                      Change photo
                    </button>
                    <span class="text-[11px] font-light text-bone-dim">JPEG, PNG or WebP, up to 5 MB.</span>
                    <p v-if="avatarError" class="text-xs text-rose-300">{{ avatarError }}</p>
                  </div>
                  <input
                    ref="avatarInput"
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    class="hidden"
                    @change="onAvatarChange"
                  />
                </div>

                <div class="grid grid-cols-2 gap-3">
                  <label class="flex flex-col gap-1">
                    <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">First name</span>
                    <input
                      v-model="firstName"
                      type="text"
                      class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
                    />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Last name</span>
                    <input
                      v-model="lastName"
                      type="text"
                      class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
                    />
                  </label>
                </div>

                <label class="flex flex-col gap-1">
                  <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Phone</span>
                  <input
                    v-model="phone"
                    type="tel"
                    placeholder="Not on file"
                    class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone placeholder:text-bone-dim/50 font-light py-1 transition-colors"
                  />
                </label>

                <!-- Email and date of birth are read-only here on purpose: email change is a
                     different security surface, and date of birth was age-verified at
                     registration — editing it here would be a quiet way around that check. -->
                <div class="flex flex-col gap-1">
                  <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Email</span>
                  <span class="text-sm font-light text-bone-dim">{{ profile.email }}</span>
                </div>
                <div class="flex flex-col gap-1">
                  <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Date of birth</span>
                  <span class="text-sm font-light text-bone-dim">{{ formatDate(profile.dateOfBirth) }}</span>
                </div>
                <div class="flex flex-col gap-1">
                  <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Member since</span>
                  <span class="text-sm font-light text-bone-dim">{{ memberSince(profile.createdAt) }}</span>
                </div>

                <p v-if="error" class="text-xs text-rose-300">{{ error }}</p>
                <p v-else-if="saved" class="text-xs text-champagne">Saved.</p>

                <button
                  type="button"
                  :disabled="!dirty || saving"
                  class="mt-1 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed w-fit"
                  @click="saveProfile"
                >
                  <Loader2 v-if="saving" class="w-4 h-4 animate-spin" aria-hidden="true" />
                  Save changes
                </button>
              </template>
            </div>

            <!-- Login & security -->
            <div v-else-if="modal.tab === 'security'" class="flex flex-col gap-5 max-w-sm">
              <h3 class="font-display text-2xl text-bone mb-1">Login & security</h3>

              <!-- Real password change. currentPassword can be left blank for an account that
                   has never set one (e.g. signed up via Google) — the server skips proving
                   it in that case rather than rejecting the request. -->
              <form class="flex flex-col gap-3 p-4 rounded-[1.25rem] bg-bone/5 border border-hairline" @submit.prevent="savePassword">
                <div class="flex items-center gap-2">
                  <KeyRound class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
                  <p class="text-sm text-bone">Password</p>
                </div>
                <p class="text-xs font-light text-bone-dim -mt-1">
                  A one-time code is also sent to your email on every sign-in, as a second step.
                </p>

                <label class="flex flex-col gap-1">
                  <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Current password</span>
                  <input
                    v-model="currentPassword"
                    type="password"
                    autocomplete="current-password"
                    placeholder="Leave blank if you've never set one"
                    class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone placeholder:text-bone-dim/50 font-light py-1 transition-colors"
                  />
                </label>
                <label class="flex flex-col gap-1">
                  <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">New password</span>
                  <input
                    v-model="newPassword"
                    type="password"
                    minlength="8"
                    autocomplete="new-password"
                    class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
                  />
                </label>
                <label class="flex flex-col gap-1">
                  <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Confirm new password</span>
                  <input
                    v-model="confirmNewPassword"
                    type="password"
                    autocomplete="new-password"
                    class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1 transition-colors"
                  />
                  <span v-if="confirmNewPassword && newPassword !== confirmNewPassword" class="text-xs text-rose-300">
                    Passwords don't match.
                  </span>
                </label>

                <p v-if="passwordError" class="text-xs text-rose-300">{{ passwordError }}</p>
                <p v-else-if="passwordSaved" class="text-xs text-champagne">Password updated.</p>

                <button
                  type="submit"
                  :disabled="!newPasswordValid || passwordSaving"
                  class="mt-1 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed w-fit"
                >
                  <Loader2 v-if="passwordSaving" class="w-4 h-4 animate-spin" aria-hidden="true" />
                  Update password
                </button>
              </form>

              <div v-if="profile" class="flex items-start gap-3 p-4 rounded-[1.25rem] bg-bone/5 border border-hairline">
                <ShieldCheck class="w-4 h-4 text-champagne shrink-0 mt-0.5" aria-hidden="true" />
                <div>
                  <p class="text-sm text-bone flex items-center gap-2">
                    Email verification
                    <span
                      class="text-[10px] font-medium uppercase tracking-wide rounded-full px-2 py-0.5"
                      :class="profile.emailVerified ? 'text-champagne border border-champagne-dim' : 'text-bone-dim border border-hairline'"
                    >
                      {{ profile.emailVerified ? 'Verified' : 'Not verified' }}
                    </span>
                  </p>
                  <p class="text-xs font-light text-bone-dim mt-1">{{ profile.email }}</p>
                </div>
              </div>

              <p v-if="profile && profile.accountStatus !== 'APPROVED'" class="text-xs text-amber-300/90">
                Account status: {{ profile.accountStatus }}
              </p>
            </div>

            <!-- Languages & currency -->
            <div v-else class="flex flex-col gap-4 max-w-sm">
              <h3 class="font-display text-2xl text-bone mb-1">Languages & currency</h3>
              <p class="text-xs font-light text-bone-dim leading-relaxed">
                Folio is English-only for now. Every price is charged in EUR — choosing another
                currency adds an estimated conversion next to the price, using a fixed rate, not
                a live market rate.
              </p>

              <ul class="flex flex-col gap-1" role="radiogroup" aria-label="Display currency">
                <li v-for="c in CURRENCIES" :key="c.code">
                  <button
                    type="button"
                    role="radio"
                    :aria-checked="currency.code === c.code"
                    class="w-full flex items-center justify-between px-3 py-2.5 rounded-lg text-sm font-light transition-colors"
                    :class="currency.code === c.code ? 'bg-bone/8 text-bone' : 'text-bone-dim hover:bg-bone/5 hover:text-bone'"
                    @click="pickCurrency(c.code)"
                  >
                    <span>{{ c.label }} <span class="text-bone-dim/70">· {{ c.code }}</span></span>
                    <Check v-if="currency.code === c.code" class="w-4 h-4 text-champagne" aria-hidden="true" />
                  </button>
                </li>
              </ul>
            </div>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
