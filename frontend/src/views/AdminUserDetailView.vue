<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ArrowLeft, Loader2, RotateCw } from 'lucide-vue-next'
import ExtranetShell from '@/components/ExtranetShell.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { adminApi } from '@/api/admin'
import { bookingsApi } from '@/api/bookings'
import { apiErrorMessage, resolveUploadUrl } from '@/api/http'
import { useConfirmModalStore } from '@/stores/confirmModal'
import { useCurrencyStore } from '@/stores/currency'
import { formatDateRange } from '@/lib/dates'
import { accountStatusLabel, type AccountStatus, type UserProfileResponse } from '@/types/account'
import { roleLabel } from '@/types/auth'
import { bookingStatusLabel, type BookingResponse, type BookingStatus } from '@/types/booking'

const { t } = useI18n()
const route = useRoute()
const confirmModal = useConfirmModalStore()
const currency = useCurrencyStore()

const userId = computed(() => Number(route.params.id))

const user = ref<UserProfileResponse | null>(null)
const loading = ref(true)
const error = ref('')
const statusActing = ref(false)

const bookings = ref<BookingResponse[]>([])
const bookingsLoading = ref(true)
const bookingsError = ref('')
const bookingActing = ref<number | null>(null)

const STATUS_CLASSES: Record<AccountStatus, string> = {
  PENDING: 'text-amber-300/90 bg-amber-300/10 border-amber-300/20',
  APPROVED: 'text-champagne bg-champagne/10 border-champagne/25',
  REJECTED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  SUSPENDED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  ANONYMIZED: 'text-bone-dim bg-bone/5 border-hairline',
  BANNED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  DEACTIVATED: 'text-bone-dim bg-bone/5 border-hairline',
  LOCKED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
}

const BOOKING_STATUS_CLASSES: Record<BookingStatus, string> = {
  PENDING: 'text-amber-300/90 bg-amber-300/10 border-amber-300/20',
  CONFIRMED: 'text-champagne bg-champagne/10 border-champagne/25',
  CHECKED_IN: 'text-sky-300/90 bg-sky-300/10 border-sky-300/20',
  COMPLETED: 'text-bone-dim bg-bone/5 border-hairline',
  CANCELLED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  NO_SHOW: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  PAYMENT_FAILED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
}

async function load() {
  loading.value = true
  error.value = ''
  try {
    user.value = await adminApi.getUser(userId.value)
  } catch (e) {
    error.value = apiErrorMessage(e, t('adminUserDetail.loadError'))
  } finally {
    loading.value = false
  }
}

async function loadBookings() {
  bookingsLoading.value = true
  bookingsError.value = ''
  try {
    bookings.value = await bookingsApi.getByUser(userId.value)
  } catch (e) {
    bookingsError.value = apiErrorMessage(e, t('adminUserDetail.bookingsLoadError'))
  } finally {
    bookingsLoading.value = false
  }
}

interface StatusAction {
  status: AccountStatus
  labelKey: string
  danger?: boolean
}

// Every status the account isn't already in is technically reachable through the same
// PATCH — these three are the ones an admin actually needs a one-click button for; any
// rarer transition (REJECTED, ANONYMIZED, ...) isn't a day-to-day moderation action.
const STATUS_ACTIONS: StatusAction[] = [
  { status: 'SUSPENDED', labelKey: 'adminUserDetail.suspend', danger: true },
  { status: 'BANNED', labelKey: 'adminUserDetail.ban', danger: true },
  { status: 'APPROVED', labelKey: 'adminUserDetail.reinstate' },
]

const availableStatusActions = computed(() =>
  user.value ? STATUS_ACTIONS.filter((a) => a.status !== user.value!.accountStatus) : [],
)

async function changeStatus(action: StatusAction) {
  if (!user.value) return
  if (action.danger) {
    const confirmed = await confirmModal.ask({
      title: t(action.labelKey),
      message: t('adminUserDetail.statusConfirmMessage', {
        name: `${user.value.firstName} ${user.value.lastName}`,
        status: accountStatusLabel(action.status),
      }),
      confirmLabel: t(action.labelKey),
      cancelLabel: t('adminUserDetail.back'),
      danger: true,
    })
    if (!confirmed) return
  }
  statusActing.value = true
  error.value = ''
  try {
    user.value = await adminApi.updateStatus(userId.value, action.status)
  } catch (e) {
    error.value = apiErrorMessage(e, t('adminUserDetail.statusError'))
  } finally {
    statusActing.value = false
  }
}

type BookingActionKind = 'confirm' | 'checkIn' | 'complete' | 'cancel'

const BOOKING_ACTION_LABEL_KEYS: Record<BookingActionKind, string> = {
  confirm: 'manageBookings.actionConfirm',
  checkIn: 'manageBookings.actionCheckIn',
  complete: 'manageBookings.actionComplete',
  cancel: 'manageBookings.actionCancel',
}

/** Mirrors ManageBookingsView's own transition map — the admin board drives the exact
 *  same state machine, just scoped to one guest instead of one company. */
function bookingActionsFor(
  booking: BookingResponse,
): { kind: BookingActionKind; run: () => Promise<BookingResponse>; danger?: boolean }[] {
  switch (booking.bookingStatus) {
    case 'PENDING':
      return [
        { kind: 'confirm', run: () => bookingsApi.confirm(booking.id) },
        { kind: 'cancel', run: () => bookingsApi.cancel(booking.id), danger: true },
      ]
    case 'CONFIRMED':
      return [
        { kind: 'checkIn', run: () => bookingsApi.checkIn(booking.id) },
        { kind: 'cancel', run: () => bookingsApi.cancel(booking.id), danger: true },
      ]
    case 'CHECKED_IN':
      return [{ kind: 'complete', run: () => bookingsApi.complete(booking.id) }]
    default:
      return []
  }
}

async function runBookingAction(
  booking: BookingResponse,
  action: { kind: BookingActionKind; run: () => Promise<BookingResponse>; danger?: boolean },
) {
  if (action.danger) {
    const confirmed = await confirmModal.ask({
      title: t('manageBookings.cancelModalTitle'),
      message: t('manageBookings.cancelModalMessage', { user: booking.userFullName, hotel: booking.hotelName }),
      confirmLabel: t(BOOKING_ACTION_LABEL_KEYS[action.kind]),
      cancelLabel: t('manageBookings.back'),
      danger: true,
    })
    if (!confirmed) return
  }
  bookingActing.value = booking.id
  bookingsError.value = ''
  try {
    await action.run()
    await loadBookings()
  } catch (e) {
    bookingsError.value = apiErrorMessage(e, t('manageBookings.updateError'))
  } finally {
    bookingActing.value = null
  }
}

onMounted(() => {
  load()
  loadBookings()
})
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <ExtranetShell :badge="$t('adminUsers.badge')" />

    <main class="flex-1 px-6 md:px-10 py-10 max-w-4xl mx-auto w-full">
      <RouterLink
        :to="{ name: 'admin-users' }"
        class="flex items-center gap-2 text-sm font-light text-bone-dim hover:text-bone transition-colors mb-6 w-fit"
      >
        <ArrowLeft class="w-4 h-4" aria-hidden="true" />
        {{ $t('adminUserDetail.backToList') }}
      </RouterLink>

      <div v-if="loading" class="flex flex-col gap-3">
        <div class="h-32 rounded-[1.25rem] bg-ink-2 border border-hairline animate-pulse" />
      </div>

      <div v-else-if="error && !user" class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4">
        <p class="text-sm text-rose-300">{{ error }}</p>
        <button
          type="button"
          class="flex items-center gap-2 rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors"
          @click="load"
        >
          <RotateCw class="w-4 h-4" aria-hidden="true" />
          {{ $t('adminUserDetail.retry') }}
        </button>
      </div>

      <template v-else-if="user">
        <div class="rounded-[1.25rem] bg-ink-2 border border-hairline p-6 md:p-8 mb-8">
          <div class="flex flex-wrap items-start justify-between gap-6">
            <div class="flex items-center gap-4 min-w-0">
              <img
                v-if="user.avatarUrl"
                :src="resolveUploadUrl(user.avatarUrl)!"
                alt=""
                class="w-14 h-14 rounded-full object-cover shrink-0"
              />
              <div
                v-else
                class="w-14 h-14 rounded-full bg-champagne/15 text-champagne flex items-center justify-center text-xl font-medium shrink-0"
              >
                {{ user.firstName.charAt(0).toUpperCase() }}
              </div>
              <div class="min-w-0">
                <h1 class="font-display text-2xl md:text-3xl text-bone truncate">{{ user.firstName }} {{ user.lastName }}</h1>
                <p class="text-sm font-light text-bone-dim mt-1 truncate">{{ user.email }}</p>
                <p v-if="user.phone" class="text-sm font-light text-bone-dim mt-0.5">{{ user.phone }}</p>
              </div>
            </div>

            <span class="px-2.5 py-1 rounded-full text-[11px] font-medium border shrink-0" :class="STATUS_CLASSES[user.accountStatus]">
              {{ accountStatusLabel(user.accountStatus) }}
            </span>
          </div>

          <div class="flex flex-wrap items-center gap-1.5 mt-5 pt-5 border-t border-hairline">
            <span
              v-for="r in user.roles"
              :key="r"
              class="px-2.5 py-1 rounded-full text-[11px] font-medium border text-bone-dim bg-bone/5 border-hairline"
            >
              {{ roleLabel(r) }}
            </span>
            <span class="text-xs font-light text-bone-dim/70 ml-auto">
              {{ $t('adminUserDetail.memberSince', { date: new Date(user.createdAt).toLocaleDateString() }) }}
            </span>
          </div>

          <div v-if="availableStatusActions.length" class="flex flex-wrap items-center gap-2 mt-5 pt-5 border-t border-hairline">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim mr-1">{{ $t('adminUserDetail.accountActions') }}</span>
            <button
              v-for="action in availableStatusActions"
              :key="action.status"
              type="button"
              :disabled="statusActing"
              class="flex items-center gap-2 rounded-full border px-4 py-2 text-xs font-light transition-colors disabled:opacity-50"
              :class="
                action.danger
                  ? 'border-hairline text-bone-dim hover:text-bone hover:border-rose-300/40'
                  : 'border-champagne/30 text-champagne hover:bg-champagne/10'
              "
              @click="changeStatus(action)"
            >
              <Loader2 v-if="statusActing" class="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
              {{ $t(action.labelKey) }}
            </button>
          </div>

          <p v-if="error" class="text-xs text-rose-300 mt-4">{{ error }}</p>
        </div>

        <h2 class="font-display text-2xl text-bone mb-4">{{ $t('adminUserDetail.bookingsTitle') }}</h2>

        <div v-if="bookingsLoading" class="flex flex-col gap-3">
          <div v-for="i in 2" :key="i" class="h-24 rounded-[1.25rem] bg-ink-2 border border-hairline animate-pulse" />
        </div>

        <div
          v-else-if="bookingsError"
          class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4"
        >
          <p class="text-sm text-rose-300">{{ bookingsError }}</p>
          <button
            type="button"
            class="flex items-center gap-2 rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors"
            @click="loadBookings"
          >
            <RotateCw class="w-4 h-4" aria-hidden="true" />
            {{ $t('adminUserDetail.retry') }}
          </button>
        </div>

        <p v-else-if="!bookings.length" class="text-sm font-light text-bone-dim">{{ $t('adminUserDetail.bookingsEmpty') }}</p>

        <div v-else class="flex flex-col gap-4">
          <article
            v-for="booking in bookings"
            :key="booking.id"
            class="rounded-[1.25rem] bg-ink-2 border border-hairline p-6 flex flex-wrap items-start justify-between gap-4"
          >
            <div class="min-w-0">
              <div class="flex items-center gap-3 flex-wrap">
                <h3 class="font-display text-xl text-bone">{{ booking.hotelName }}</h3>
                <span class="px-2.5 py-1 rounded-full text-[11px] font-medium border" :class="BOOKING_STATUS_CLASSES[booking.bookingStatus]">
                  {{ bookingStatusLabel(booking.bookingStatus) }}
                </span>
              </div>
              <p class="text-sm font-light text-bone-dim mt-2">
                {{ $t('payment.roomNumber', { number: booking.roomNumber }) }} ·
                {{ formatDateRange(booking.checkIn, booking.checkOut) }} ·
                {{ booking.guestCount }} {{ booking.guestCount === 1 ? $t('hotels.guest') : $t('hotels.guests') }}
              </p>
              <p v-if="booking.specialRequest" class="text-xs font-light text-bone-dim/80 mt-2 italic">
                "{{ booking.specialRequest }}"
              </p>
            </div>

            <div class="flex flex-col items-end gap-3">
              <p class="text-right">
                <span class="font-display text-2xl text-bone">{{ currency.format(booking.totalPrice) }}</span>
                <span v-if="currency.estimate(booking.totalPrice)" class="block text-[11px] font-light text-bone-dim">
                  {{ currency.estimate(booking.totalPrice) }}
                </span>
              </p>
              <div class="flex items-center gap-2">
                <button
                  v-for="action in bookingActionsFor(booking)"
                  :key="action.kind"
                  type="button"
                  :disabled="bookingActing === booking.id"
                  class="flex items-center gap-2 rounded-full border px-4 py-2 text-xs font-light transition-colors disabled:opacity-50"
                  :class="
                    action.danger
                      ? 'border-hairline text-bone-dim hover:text-bone hover:border-rose-300/40'
                      : 'border-champagne/30 text-champagne hover:bg-champagne/10'
                  "
                  @click="runBookingAction(booking, action)"
                >
                  <Loader2 v-if="bookingActing === booking.id" class="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                  {{ $t(BOOKING_ACTION_LABEL_KEYS[action.kind]) }}
                </button>
              </div>
            </div>
          </article>
        </div>
      </template>
    </main>

    <SiteFooter />
  </div>
</template>
