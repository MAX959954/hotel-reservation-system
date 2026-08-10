<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loader2, RotateCw } from 'lucide-vue-next'
import Navbar from '@/components/Navbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { bookingsApi } from '@/api/bookings'
import { companiesApi } from '@/api/companies'
import { companyUsersApi } from '@/api/companyUsers'
import { apiErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useCompanyStore } from '@/stores/company'
import { useConfirmModalStore } from '@/stores/confirmModal'
import { useCurrencyStore } from '@/stores/currency'
import { formatDateRange } from '@/lib/dates'
import { bookingStatusLabel, type BookingResponse, type BookingStatus } from '@/types/booking'
import { companyRoleLabel } from '@/types/companyUser'

const { t } = useI18n()
const auth = useAuthStore()
const company = useCompanyStore()
const confirmModal = useConfirmModalStore()
const currency = useCurrencyStore()

// Mirrors the backend's own @PreAuthorize bypass on these endpoints: ADMIN/RECEPTIONIST/
// HOTEL_MANAGER can act on any company's bookings, not just ones they're a company_user
// row for — so for them the picker is "every active company", not "my memberships".
const hasGlobalAccess = computed(
  () => auth.hasRole('ADMIN') || auth.hasRole('RECEPTIONIST') || auth.hasRole('HOTEL_MANAGER'),
)
const allCompanies = ref<{ companyId: number; companyName: string }[]>([])

const STATUS_CLASSES: Record<BookingStatus, string> = {
  PENDING: 'text-amber-300/90 bg-amber-300/10 border-amber-300/20',
  CONFIRMED: 'text-champagne bg-champagne/10 border-champagne/25',
  CHECKED_IN: 'text-sky-300/90 bg-sky-300/10 border-sky-300/20',
  COMPLETED: 'text-bone-dim bg-bone/5 border-hairline',
  CANCELLED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  NO_SHOW: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  PAYMENT_FAILED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
}

const FILTERS = computed<{ label: string; value: BookingStatus | null }[]>(() => [
  { label: t('manageBookings.filterPending'), value: 'PENDING' },
  { label: t('manageBookings.filterConfirmed'), value: 'CONFIRMED' },
  { label: t('manageBookings.filterCheckedIn'), value: 'CHECKED_IN' },
  { label: t('manageBookings.filterCompleted'), value: 'COMPLETED' },
  { label: t('manageBookings.filterCancelled'), value: 'CANCELLED' },
  { label: t('manageBookings.filterAll'), value: null },
])

const invited = computed(() => company.memberships.filter((m) => m.status === 'INVITED'))
const myCompanies = computed(() => company.active)
const pickableCompanies = computed(() => (hasGlobalAccess.value ? allCompanies.value : myCompanies.value))

const companyId = ref<number | null>(null)
const status = ref<BookingStatus | null>('PENDING')
const bookings = ref<BookingResponse[]>([])
const loading = ref(true)
const error = ref('')
const acting = ref<number | null>(null)

type ActionKind = 'confirm' | 'checkIn' | 'complete' | 'cancel'

const ACTION_LABEL_KEYS: Record<ActionKind, string> = {
  confirm: 'manageBookings.actionConfirm',
  checkIn: 'manageBookings.actionCheckIn',
  complete: 'manageBookings.actionComplete',
  cancel: 'manageBookings.actionCancel',
}

/** Server-side transitions this panel drives: confirm, check-in, complete, cancel.
 *  Only a `kind` travels here, not a pre-translated label — the label and (for the
 *  danger action) the confirmation copy are both resolved from it separately, since
 *  concatenating a translated verb into an English sentence template would produce
 *  broken grammar in most other languages. */
function actionsFor(booking: BookingResponse): { kind: ActionKind; run: () => Promise<BookingResponse>; danger?: boolean }[] {
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

async function load() {
  if (!companyId.value) return
  loading.value = true
  error.value = ''
  try {
    bookings.value = await bookingsApi.getByCompany(companyId.value, status.value ?? undefined)
  } catch (e) {
    error.value = apiErrorMessage(e, t('manageBookings.loadError'))
  } finally {
    loading.value = false
  }
}

async function runAction(
  booking: BookingResponse,
  action: { kind: ActionKind; run: () => Promise<BookingResponse>; danger?: boolean },
) {
  if (action.danger) {
    // Only 'cancel' is ever marked danger, so this modal copy doesn't need to vary by kind.
    const confirmed = await confirmModal.ask({
      title: t('manageBookings.cancelModalTitle'),
      message: t('manageBookings.cancelModalMessage', { user: booking.userFullName, hotel: booking.hotelName }),
      confirmLabel: t(ACTION_LABEL_KEYS[action.kind]),
      cancelLabel: t('manageBookings.back'),
      danger: true,
    })
    if (!confirmed) return
  }
  acting.value = booking.id
  error.value = ''
  try {
    await action.run()
    // Refetch rather than patch in place: several fields (room status, confirmedAt, ...)
    // change server-side and the filtered list itself may need to drop this row.
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e, t('manageBookings.updateError'))
  } finally {
    acting.value = null
  }
}

async function accept(membershipId: number) {
  error.value = ''
  try {
    await companyUsersApi.acceptInvite(membershipId)
    company.reset()
    await company.load()
  } catch (e) {
    error.value = apiErrorMessage(e, t('manageBookings.acceptError'))
  }
}

watch(pickableCompanies, (list) => {
  if (!companyId.value && list.length) companyId.value = list[0].companyId
})
watch([companyId, status], load)

onMounted(async () => {
  const tasks: Promise<unknown>[] = [company.load()]
  if (hasGlobalAccess.value) {
    tasks.push(companiesApi.getByStatus('ACTIVE').then((list) => {
      allCompanies.value = list.map((c) => ({ companyId: c.id, companyName: c.name }))
    }))
  }
  await Promise.all(tasks)
  if (pickableCompanies.value.length) companyId.value = pickableCompanies.value[0].companyId
  else loading.value = false
})
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <div class="border-b border-hairline">
      <Navbar />
    </div>

    <main class="flex-1 px-6 md:px-10 py-10 max-w-5xl mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-4">{{ $t('manageBookings.title') }}</h1>

      <div
        v-for="member in invited"
        :key="member.id"
        class="flex flex-wrap items-center justify-between gap-3 rounded-[1.25rem] border border-champagne/25 bg-champagne/5 p-4 mb-4"
      >
        <i18n-t keypath="manageBookings.invitedMessage" tag="p" class="text-sm font-light text-bone">
          <template #company><span class="text-bone">{{ member.companyName }}</span></template>
          <template #role>{{ companyRoleLabel(member.companyRole) }}</template>
        </i18n-t>
        <button
          type="button"
          class="rounded-full bg-champagne text-ink px-4 py-2 text-xs font-medium hover:bg-champagne-bright transition-colors"
          @click="accept(member.id)"
        >
          {{ $t('manageBookings.accept') }}
        </button>
      </div>

      <div v-if="!pickableCompanies.length" class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8">
        <p class="text-sm font-light text-bone-dim">{{ $t('manageBookings.noProperties') }}</p>
      </div>

      <template v-else>
        <div class="flex flex-wrap items-center gap-2 mb-6">
          <button
            v-for="member in pickableCompanies"
            :key="member.companyId"
            type="button"
            class="px-3 py-1.5 rounded-full text-xs font-light transition-colors border"
            :class="
              companyId === member.companyId
                ? 'bg-bone/8 text-bone border-hairline'
                : 'text-bone-dim border-transparent hover:text-bone hover:bg-bone/5'
            "
            @click="companyId = member.companyId"
          >
            {{ member.companyName }}
          </button>
        </div>

        <div class="flex flex-wrap gap-2 mb-6">
          <button
            v-for="f in FILTERS"
            :key="f.value ?? 'all'"
            type="button"
            role="tab"
            :aria-selected="status === f.value"
            class="px-3 py-1.5 rounded-full text-xs font-light transition-colors"
            :class="status === f.value ? 'bg-champagne text-ink' : 'text-bone-dim bg-bone/5 hover:text-bone'"
            @click="status = f.value"
          >
            {{ f.label }}
          </button>
        </div>

        <div v-if="loading" class="flex flex-col gap-3">
          <div v-for="i in 3" :key="i" class="h-28 rounded-[1.25rem] bg-ink-2 border border-hairline animate-pulse" />
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
            {{ $t('manageBookings.retry') }}
          </button>
        </div>

        <p v-else-if="!bookings.length" class="text-sm font-light text-bone-dim">
          {{ $t('manageBookings.empty') }}
        </p>

        <div v-else class="flex flex-col gap-4">
          <article
            v-for="booking in bookings"
            :key="booking.id"
            class="rounded-[1.25rem] bg-ink-2 border border-hairline p-6 flex flex-wrap items-start justify-between gap-4"
          >
            <div class="min-w-0">
              <div class="flex items-center gap-3 flex-wrap">
                <h2 class="font-display text-xl text-bone">{{ booking.hotelName }}</h2>
                <span
                  class="px-2.5 py-1 rounded-full text-[11px] font-medium border"
                  :class="STATUS_CLASSES[booking.bookingStatus]"
                >
                  {{ bookingStatusLabel(booking.bookingStatus) }}
                </span>
              </div>
              <p class="text-sm font-light text-bone-dim mt-2">
                {{ booking.userFullName }} · {{ $t('payment.roomNumber', { number: booking.roomNumber }) }} ·
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
              <div class="flex items-center gap-2">
                <button
                  v-for="action in actionsFor(booking)"
                  :key="action.kind"
                  type="button"
                  :disabled="acting === booking.id"
                  class="flex items-center gap-2 rounded-full border px-4 py-2 text-xs font-light transition-colors disabled:opacity-50"
                  :class="
                    action.danger
                      ? 'border-hairline text-bone-dim hover:text-bone hover:border-rose-300/40'
                      : 'border-champagne/30 text-champagne hover:bg-champagne/10'
                  "
                  @click="runAction(booking, action)"
                >
                  <Loader2 v-if="acting === booking.id" class="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                  {{ $t(ACTION_LABEL_KEYS[action.kind]) }}
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
