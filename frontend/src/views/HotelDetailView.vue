<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { hotelsApi } from '../api/hotels'
import { roomsApi } from '../api/rooms'
import { reviewsApi } from '../api/reviews'
import { useAuthStore } from '../stores/auth'
import { useAuthModalStore } from '../stores/authModal'
import { useCurrencyStore } from '../stores/currency'
import AmenityIcon from '../components/AmenityIcon.vue'
import BookingModal from '../components/BookingModal.vue'
import type { HotelResponse } from '../types/hotel'
import type { RoomResponse } from '../types/room'
import type { ReviewResponse } from '../types/review'

const props = defineProps<{ id: number }>()

const auth = useAuthStore()
const authModal = useAuthModalStore()
const currency = useCurrencyStore()
const { t, locale } = useI18n()

const hotel = ref<HotelResponse | null>(null)
const rooms = ref<RoomResponse[]>([])
const loading = ref(true)
const error = ref('')

// Real reviews only — pulled per room from the actual reviews backend (a room's approved
// entries), never fabricated testimonials. A hotel has no review field of its own, so this
// aggregates across whichever rooms have any on file.
const reviews = ref<ReviewResponse[]>([])
const reviewsLoading = ref(true)

const reviewCount = computed(() => reviews.value.length)
const averageRating = computed(() => {
  if (reviews.value.length === 0) return null
  const sum = reviews.value.reduce((acc, r) => acc + r.rating, 0)
  return Math.round((sum / reviews.value.length) * 10) / 10
})

const RELATIVE_UNITS: [Intl.RelativeTimeFormatUnit, number][] = [
  ['year', 1000 * 60 * 60 * 24 * 365],
  ['month', 1000 * 60 * 60 * 24 * 30],
  ['week', 1000 * 60 * 60 * 24 * 7],
  ['day', 1000 * 60 * 60 * 24],
]

function formatReviewDate(iso: string) {
  const diff = new Date(iso).getTime() - Date.now()
  const rtf = new Intl.RelativeTimeFormat(locale.value, { numeric: 'auto' })
  for (const [unit, ms] of RELATIVE_UNITS) {
    if (Math.abs(diff) >= ms || unit === 'day') {
      return rtf.format(Math.round(diff / ms), unit)
    }
  }
  return rtf.format(0, 'day')
}

function nightsStayed(review: ReviewResponse) {
  const nights = Math.round(
    (new Date(review.stayCheckOut).getTime() - new Date(review.stayCheckIn).getTime()) /
      (1000 * 60 * 60 * 24),
  )
  return Math.max(nights, 1)
}

function initialsFor(fullName: string) {
  return fullName
    .split(' ')
    .filter(Boolean)
    .slice(0, 2)
    .map((n) => n[0]?.toUpperCase())
    .join('')
}

const mapEmbedUrl = computed(() => {
  if (!hotel.value) return ''
  const query = encodeURIComponent(`${hotel.value.address}, ${hotel.value.city}, ${hotel.value.country}`)
  return `https://www.google.com/maps?q=${query}&output=embed`
})
const mapLinkUrl = computed(() => {
  if (!hotel.value) return '#'
  const query = encodeURIComponent(`${hotel.value.address}, ${hotel.value.city}, ${hotel.value.country}`)
  return `https://www.google.com/maps?q=${query}`
})

// Undated room list on load (the "/available" endpoint requires a date range and
// 400s without one); once a visitor checks real dates, this narrows to true availability.
const filteredForDates = ref(false)
const availCheckIn = ref('')
const availCheckOut = ref('')
const availGuests = ref(1)
const availLoading = ref(false)
const availError = ref('')

const showBookingModal = ref(false)
const bookingInitialRoomId = ref<number | null>(null)

// The backend rejects a check-in/out that isn't strictly in the future (@Future
// validation) — cap what the date pickers will even offer so that error, previously
// surfaced only as a generic "Validation failed", can't happen from the UI anymore.
const today = new Date().toISOString().slice(0, 10)
const availMinCheckOut = computed(() => availCheckIn.value || today)

const cheapestNightly = computed(() => {
  if (rooms.value.length === 0) return null
  return Math.min(...rooms.value.map((r) => r.pricePerNight))
})

// date inputs give a plain "YYYY-MM-DD" — guests pick a date, not an exact arrival
// minute (matching how every OTA does this), so a standard hotel check-in/check-out
// time is appended to satisfy the backend's LocalDateTime binding, which needs the
// full "YYYY-MM-DDTHH:mm:ss" form (not toISOString()'s UTC "Z"-suffixed form, which
// it can't parse -> 500).
const STANDARD_CHECK_IN_TIME = '15:00:00'
const STANDARD_CHECK_OUT_TIME = '11:00:00'

function toCheckInDateTime(date: string) {
  return `${date}T${STANDARD_CHECK_IN_TIME}`
}
function toCheckOutDateTime(date: string) {
  return `${date}T${STANDARD_CHECK_OUT_TIME}`
}

const acronyms: Record<string, string> = { WIFI: 'WiFi', TV: 'TV', EV: 'EV' }

function humanize(value: string) {
  return value
    .split('_')
    .map((w) => acronyms[w] ?? w[0].toUpperCase() + w.slice(1).toLowerCase())
    .join(' ')
}

const placeholderPhotos = [
  'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=1200&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=1200&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=1200&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=1200&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=1200&q=80&auto=format&fit=crop',
]

const photo = computed(() => {
  if (!hotel.value) return placeholderPhotos[0]
  return hotel.value.imageUrl || placeholderPhotos[hotel.value.id % placeholderPhotos.length]
})

onMounted(async () => {
  try {
    const [hotelRes, roomsRes] = await Promise.all([
      hotelsApi.getById(props.id),
      roomsApi.getByHotel(props.id),
    ])
    hotel.value = hotelRes
    rooms.value = roomsRes
  } catch (err: any) {
    error.value = err.response?.data?.message ?? t('hotelDetail.errorFallback')
  } finally {
    loading.value = false
  }

  if (rooms.value.length > 0) {
    try {
      const perRoom = await Promise.all(
        rooms.value.map((room) => reviewsApi.getApprovedByRoom(room.id)),
      )
      reviews.value = perRoom
        .flat()
        .sort((a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime())
    } catch {
      reviews.value = []
    } finally {
      reviewsLoading.value = false
    }
  } else {
    reviewsLoading.value = false
  }
})

// The backend's validation-error response carries the generic "Validation failed" as
// `message` and the actual per-field reason(s) in `errors` (e.g. "checkIn: must be a
// future date") — prefer that specific reason when there is one.
function apiErrorMessage(err: any, fallback: string): string {
  const errors = err.response?.data?.errors
  if (Array.isArray(errors) && errors.length > 0) return errors.join(' ')
  return err.response?.data?.message ?? fallback
}

async function checkAvailability() {
  if (!availCheckIn.value || !availCheckOut.value) return
  availLoading.value = true
  availError.value = ''
  try {
    rooms.value = await roomsApi.getAvailableForHotel(
      props.id,
      toCheckInDateTime(availCheckIn.value),
      toCheckOutDateTime(availCheckOut.value),
      availGuests.value,
    )
    filteredForDates.value = true
  } catch (err: any) {
    availError.value = apiErrorMessage(err, t('hotelDetail.availabilityErrorFallback'))
  } finally {
    availLoading.value = false
  }
}

// The card's single "Choose room" action folds Airbnb's separate "check dates" step
// into one click: fetch real availability, then open the room picker on success.
async function chooseRoom() {
  bookingInitialRoomId.value = null
  await checkAvailability()
  if (!availError.value) showBookingModal.value = true
}

function bookRoom(roomId: number) {
  bookingInitialRoomId.value = roomId
  showBookingModal.value = true
}
</script>

<template>
  <!--
    THESIS: a hotel's page is its own entry in the register, not a card-and-sidebar
    listing shell — a single bounded ledger page (paper, ruled, pinned photo) holding
    particulars, description, an availability slip, and a room ledger table. Distinct
    from the homepage's two-page spread (an index of many) and from login/register's
    Spine (nothing to browse there); this page has real content to read, so it earns
    the full paper-page treatment. Extension of the established Folio world — no new
    palette, no concept tournament (new-work.md "Extend an existing surface" / precisely
    specified narrow request per the user's own reference + "but different" brief).
    Evidence discipline: no invented photo gallery, amenities, or reviews — only fields
    HotelResponse/RoomResponse actually return (PRODUCT.md "Evidence on Hand").
    FOLIO: VI — continues I/II (hero), III (login), IV (register), V (search).
  -->
  <div class="detail-page">
    <p v-if="loading" class="status-note">{{ t('hotelDetail.loading') }}</p>
    <p v-else-if="error" class="status-note error">{{ error }}</p>

    <article v-else-if="hotel" class="entry-page">
      <div class="entry-photo">
        <img :src="photo" :alt="hotel.name" />
        <span v-if="hotel.status !== 'ACTIVE'" class="ink-stamp status-stamp">
          {{ humanize(hotel.status) }}
        </span>
      </div>

      <div class="entry-body">
        <p class="folio-no">{{ t('hotelDetail.folioSix') }}</p>
        <h1>{{ hotel.name }}</h1>
        <p class="rating" aria-hidden="true">{{ '★'.repeat(hotel.startRating) }}</p>
        <p class="location">{{ hotel.address }}, {{ hotel.city }}, {{ hotel.country }}</p>

        <dl class="particulars">
          <div>
            <dt>{{ t('hotelDetail.phoneLabel') }}</dt>
            <dd>{{ hotel.phone }}</dd>
          </div>
          <div>
            <dt>{{ t('hotelDetail.emailLabel') }}</dt>
            <dd>{{ hotel.email }}</dd>
          </div>
          <div>
            <dt>{{ t('hotelDetail.listedByLabel') }}</dt>
            <dd>{{ hotel.companyName }}</dd>
          </div>
        </dl>

        <p class="description">{{ hotel.description }}</p>

        <section v-if="hotel.amenities?.length" class="offers-section">
          <h2>{{ t('hotelDetail.offersTitle') }}</h2>
          <ul class="offers-list">
            <li v-for="amenity in hotel.amenities" :key="amenity">
              <AmenityIcon :amenity="amenity" />
              <span>{{ humanize(amenity) }}</span>
            </li>
          </ul>
        </section>

        <section class="reserve-card">
          <p v-if="cheapestNightly !== null" class="reserve-from">
            {{ t('hotelDetail.reserve.fromPrice', { price: currency.format(cheapestNightly) }) }}
          </p>
          <form class="avail-form" @submit.prevent="chooseRoom">
            <label class="field">
              <span class="field-label">{{ t('hotelDetail.checkIn') }}</span>
              <input v-model="availCheckIn" type="date" :min="today" required />
            </label>
            <label class="field">
              <span class="field-label">{{ t('hotelDetail.checkOut') }}</span>
              <input v-model="availCheckOut" type="date" :min="availMinCheckOut" required />
            </label>
            <label class="field field-guests">
              <span class="field-label">{{ t('hotelDetail.guests') }}</span>
              <input v-model.number="availGuests" type="number" min="1" max="5" required />
            </label>
            <button type="submit" class="brass-btn" :disabled="availLoading">
              {{ availLoading ? t('hotelDetail.checkingAvailability') : t('hotelDetail.reserve.chooseRoom') }}
            </button>
          </form>
          <p v-if="availError" class="error">{{ availError }}</p>
          <p class="reserve-notes">
            {{ t('hotelDetail.reserve.freeCancellation') }} · {{ t('hotelDetail.reserve.noChargeNote') }}
          </p>
        </section>

        <h2>{{ t('hotelDetail.availableRooms') }}</h2>
        <p v-if="rooms.length === 0" class="empty-note">{{ t('hotelDetail.noRooms') }}</p>
        <p v-else-if="!filteredForDates" class="slip-note">{{ t('hotelDetail.showingAll') }}</p>
        <p v-else class="slip-note">{{ t('hotelDetail.showingAvailable') }}</p>

        <ul v-if="rooms.length > 0" class="room-ledger">
          <li v-for="room in rooms" :key="room.id" class="room-row">
            <div class="room-main">
              <div class="room-id">
                <strong>{{ humanize(room.type) }}</strong>
                <span class="room-meta">
                  {{ t('hotelDetail.roomMeta', { number: room.number, capacity: room.capacity }) }}
                  · {{ t('hotelDetail.floorLabel') }} {{ room.floor }}
                </span>
              </div>
              <span class="room-status" :class="`status-${room.status.toLowerCase()}`">
                {{ humanize(room.status) }}
              </span>
              <div class="room-price">
                {{ currency.format(room.pricePerNight) }}
                <span class="per-night">{{ t('hotelDetail.perNight') }}</span>
              </div>
              <button
                v-if="auth.isAuthenticated && room.status === 'AVAILABLE' && filteredForDates"
                type="button"
                class="brass-btn"
                @click="bookRoom(room.id)"
              >
                {{ t('hotelDetail.book') }}
              </button>
              <button
                v-else-if="!auth.isAuthenticated && room.status === 'AVAILABLE' && filteredForDates"
                type="button"
                class="login-link"
                @click="authModal.open()"
              >
                {{ t('hotelDetail.loginToBook') }}
              </button>
              <span v-else-if="room.status === 'AVAILABLE'" class="pick-dates-hint">
                {{ t('hotelDetail.reserve.pickDatesHint') }}
              </span>
            </div>
          </li>
        </ul>

        <BookingModal
          v-if="showBookingModal"
          :hotel-name="hotel.name"
          :rooms="rooms"
          :check-in="availCheckIn"
          :check-out="availCheckOut"
          :guest-count="availGuests"
          :initial-room-id="bookingInitialRoomId"
          @close="showBookingModal = false"
        />

        <!-- Guest book: real approved reviews pulled per room, never fabricated testimonials
             (the hotel model has no review field of its own — this aggregates rooms that have any). -->
        <section class="guest-book">
          <h2>{{ t('hotelDetail.reviewsTitle') }}</h2>
          <p v-if="reviewsLoading" class="empty-note">{{ t('hotelDetail.loading') }}</p>
          <template v-else-if="reviewCount > 0">
            <p class="reviews-summary">
              <span class="avg" aria-hidden="true">★ {{ averageRating }}</span>
              {{ t('hotelDetail.reviewsSummary', { count: reviewCount }) }}
            </p>
            <ul class="review-list">
              <li v-for="review in reviews" :key="review.id" class="review-entry">
                <div class="review-seal" aria-hidden="true">{{ initialsFor(review.userFullName) }}</div>
                <div class="review-content">
                  <div class="review-meta">
                    <span class="review-name">{{ review.userFullName }}</span>
                    <span class="review-rating" aria-hidden="true">{{ '★'.repeat(review.rating) }}</span>
                  </div>
                  <p class="review-date">
                    {{
                      t('hotelDetail.reviewMeta', {
                        room: review.roomNumber,
                        nights: nightsStayed(review),
                        date: formatReviewDate(review.createdAt),
                      })
                    }}
                  </p>
                  <p class="review-comment">{{ review.comment }}</p>
                </div>
              </li>
            </ul>
          </template>
          <p v-else class="empty-note">{{ t('hotelDetail.noReviews') }}</p>
        </section>

        <!-- Where you'll be: a real map generated from the hotel's actual address. -->
        <section class="location-section">
          <h2>{{ t('hotelDetail.mapTitle') }}</h2>
          <div class="map-frame">
            <iframe
              :src="mapEmbedUrl"
              :title="t('hotelDetail.mapTitle')"
              loading="lazy"
              referrerpolicy="no-referrer-when-downgrade"
            />
          </div>
          <a :href="mapLinkUrl" target="_blank" rel="noopener" class="map-link">
            {{ t('hotelDetail.openInMaps') }}
          </a>
        </section>

        <!-- Things to know: real platform mechanics (booking status flow, cancellation rule),
             never invented per-property policy claims (check-in windows, pet rules, ID
             requirements) that this data model has no field for. -->
        <section class="know-section">
          <h2>{{ t('hotelDetail.knowTitle') }}</h2>
          <dl class="know-list">
            <div>
              <dt>{{ t('hotelDetail.knowFlowTitle') }}</dt>
              <dd>{{ t('hotelDetail.knowFlowText') }}</dd>
            </div>
            <div>
              <dt>{{ t('hotelDetail.knowCancelTitle') }}</dt>
              <dd>{{ t('hotelDetail.knowCancelText') }}</dd>
            </div>
          </dl>
        </section>
      </div>
    </article>
  </div>
</template>

<style scoped>
.detail-page {
  max-width: 980px;
  margin: 0 auto;
  padding: clamp(2rem, 5vw, 3.5rem) 1.5rem 4rem;
}

.status-note {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--text-dim);
  text-align: center;
  padding: 3rem 0;
}
.status-note.error {
  color: var(--stamp-bright);
}

/* ---------- the entry page: one bounded ledger page, not a card-and-sidebar shell ---------- */
.entry-page {
  background: var(--paper);
  border-radius: 6px;
  box-shadow: 0 30px 60px -30px rgba(0, 0, 0, 0.65), 0 2px 0 rgba(0, 0, 0, 0.25);
  overflow: hidden;
  animation: open-page 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
}
@keyframes open-page {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
@media (prefers-reduced-motion: reduce) {
  .entry-page {
    animation: none;
  }
}

.entry-photo {
  position: relative;
  aspect-ratio: 16 / 7;
  background: var(--ink-cover);
}
.entry-photo img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.ink-stamp {
  font-family: var(--mono);
  font-size: 0.78rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--stamp-bright);
  border: 2px solid var(--stamp-bright);
  border-radius: 3px;
  padding: 0.4rem 0.75rem;
  transform: rotate(-3deg);
  opacity: 0.92;
}
.status-stamp {
  position: absolute;
  bottom: 1rem;
  right: 1.25rem;
  background: var(--paper);
}

.entry-body {
  padding: clamp(1.5rem, 4vw, 2.5rem);
  background-image: repeating-linear-gradient(var(--paper) 0px, var(--paper) 27px, var(--paper-line) 28px);
  background-position-y: 6px;
  color: var(--paper-ink);
}

.folio-no {
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--paper-ink-soft);
  margin: 0 0 0.4rem;
}
.entry-body h1 {
  font-family: var(--serif);
  font-size: clamp(1.7rem, 3.5vw, 2.4rem);
  font-weight: 600;
  color: var(--paper-ink);
  margin-bottom: 0.35rem;
}
.rating {
  color: var(--brass-dim);
  letter-spacing: 0.1em;
  margin-bottom: 0.35rem;
}
.location {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--paper-ink-soft);
  margin-bottom: 1.5rem;
}

.particulars {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1rem 1.5rem;
  margin: 0 0 1.75rem;
  padding: 1rem 0;
  border-top: 1px solid var(--paper-line);
  border-bottom: 1px solid var(--paper-line);
}
.particulars dt {
  font-family: var(--mono);
  font-size: 0.68rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--paper-ink-soft);
  margin-bottom: 0.2rem;
}
.particulars dd {
  margin: 0;
  font-family: var(--serif);
  font-size: 0.98rem;
  color: var(--paper-ink);
}

.description {
  max-width: 62ch;
  line-height: 1.6;
  margin-bottom: 2rem;
}

/* ---------- what this hotel offers: a ruled list, not icon-tile cards ---------- */
.offers-section {
  margin-bottom: 2.25rem;
}
.offers-section h2 {
  font-family: var(--serif);
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--paper-ink);
  margin-bottom: 1rem;
}
.offers-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 0.75rem 2rem;
}
.offers-list li {
  display: flex;
  align-items: center;
  gap: 0.65rem;
  color: var(--paper-ink);
  font-size: 0.92rem;
}
.offers-list svg {
  flex-shrink: 0;
  color: var(--brass-dim);
}

/* ---------- the reserve card: a torn reservation slip pinned into the page,
   Folio's own take on the price-forward summary card every OTA leads with ---------- */
.reserve-card {
  background: var(--paper-2);
  border: 1px dashed var(--paper-line);
  border-radius: 3px;
  padding: 1.25rem 1.5rem;
  margin-bottom: 2.25rem;
}
.reserve-from {
  font-family: var(--serif);
  font-size: 1.15rem;
  font-weight: 600;
  color: var(--paper-ink);
  margin: 0 0 0.9rem;
}
.avail-form {
  display: flex;
  gap: 1.25rem;
  align-items: flex-end;
  flex-wrap: wrap;
}
.field {
  display: flex;
  flex-direction: column;
  gap: 0.3rem;
}
.field-guests input {
  width: 5rem;
}
.field-label {
  font-family: var(--mono);
  font-size: 0.68rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--paper-ink-soft);
}
.field input {
  border: none;
  border-bottom: 1px solid var(--paper-ink-soft);
  background: transparent;
  font-family: var(--serif);
  font-size: 0.95rem;
  color: var(--paper-ink);
  padding: 0.25rem 0;
}
.field input:focus-visible {
  outline: none;
  border-bottom-color: var(--brass-dim);
}
.slip-note {
  font-family: var(--mono);
  font-size: 0.78rem;
  color: var(--paper-ink-soft);
  margin-bottom: 1rem;
}
.reserve-notes {
  font-family: var(--mono);
  font-size: 0.74rem;
  color: var(--paper-ink-soft);
  margin: 0.85rem 0 0;
}

.brass-btn {
  font-family: var(--mono);
  font-size: 0.76rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--ink);
  background: var(--brass);
  border: none;
  border-radius: 3px;
  padding: 0.55rem 1rem;
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

.entry-body h2 {
  font-family: var(--serif);
  font-size: 1.3rem;
  font-weight: 600;
  color: var(--paper-ink);
  margin-bottom: 1rem;
}
.empty-note {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--paper-ink-soft);
}

/* ---------- the room ledger: a ruled table, not photo cards — this page IS the register ---------- */
.room-ledger {
  list-style: none;
  margin: 0;
  padding: 0;
}
.room-row {
  border-top: 1px solid var(--paper-line);
  padding: 1rem 0;
}
.room-row:last-child {
  border-bottom: 1px solid var(--paper-line);
}
.room-main {
  display: grid;
  grid-template-columns: 1.6fr auto auto auto;
  align-items: center;
  gap: 1rem;
}
.room-id {
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.room-id strong {
  font-family: var(--serif);
  font-size: 1.02rem;
  color: var(--paper-ink);
}
.room-meta {
  font-family: var(--mono);
  font-size: 0.76rem;
  color: var(--paper-ink-soft);
}
.room-status {
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  border: 1px solid var(--paper-line);
  color: var(--paper-ink-soft);
  white-space: nowrap;
}
.room-status.status-available {
  color: var(--brass-dim);
  border-color: var(--brass-dim);
}
.room-price {
  font-family: var(--mono);
  font-variant-numeric: tabular-nums;
  font-size: 0.95rem;
  color: var(--paper-ink);
  text-align: right;
  white-space: nowrap;
}
.per-night {
  display: block;
  font-size: 0.68rem;
  color: var(--paper-ink-soft);
}
.login-link {
  font-family: var(--mono);
  font-size: 0.76rem;
  color: var(--brass-dim);
  white-space: nowrap;
  background: none;
  border: none;
  padding: 0;
  cursor: pointer;
}
.login-link:hover {
  color: var(--brass-bright);
}
.pick-dates-hint {
  font-family: var(--mono);
  font-size: 0.74rem;
  color: var(--paper-ink-soft);
  font-style: italic;
  white-space: nowrap;
}

.error {
  font-family: var(--mono);
  font-size: 0.82rem;
  color: var(--stamp-bright);
}

/* ---------- guest book: real reviews, ruled entries like the room ledger ---------- */
.guest-book {
  margin-top: 2.75rem;
  padding-top: 2rem;
  border-top: 1px solid var(--paper-line);
}
.reviews-summary {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--paper-ink-soft);
  margin-bottom: 1.25rem;
}
.reviews-summary .avg {
  color: var(--brass-dim);
  font-size: 0.95rem;
  margin-right: 0.4rem;
}
.review-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}
.review-entry {
  display: flex;
  gap: 0.9rem;
  border-top: 1px solid var(--paper-line);
  padding-top: 0.9rem;
}
.review-seal {
  flex-shrink: 0;
  width: 2.1rem;
  height: 2.1rem;
  border-radius: 50%;
  background: var(--brass-dim);
  color: var(--paper);
  display: flex;
  align-items: center;
  justify-content: center;
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.02em;
  transform: rotate(-4deg);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.25);
}
.review-content {
  flex: 1;
  min-width: 0;
}
.review-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.5rem;
  margin-bottom: 0.15rem;
}
.review-name {
  font-family: var(--serif);
  font-weight: 600;
  font-size: 0.92rem;
  color: var(--paper-ink);
}
.review-rating {
  color: var(--brass-dim);
  font-size: 0.8rem;
  letter-spacing: 0.05em;
}
.review-date {
  font-family: var(--mono);
  font-size: 0.68rem;
  letter-spacing: 0.03em;
  color: var(--paper-ink-soft);
  margin: 0 0 0.4rem;
}
.review-comment {
  font-size: 0.92rem;
  line-height: 1.55;
  color: var(--paper-ink);
  max-width: 48ch;
  margin: 0;
}

/* ---------- location: a real map, framed like the hero photo ---------- */
.location-section {
  margin-top: 2.75rem;
  padding-top: 2rem;
  border-top: 1px solid var(--paper-line);
}
.map-frame {
  border: 1px solid var(--paper-line);
  border-radius: 3px;
  overflow: hidden;
  aspect-ratio: 16 / 8;
}
.map-frame iframe {
  width: 100%;
  height: 100%;
  border: none;
  display: block;
}
.map-link {
  display: inline-block;
  margin-top: 0.75rem;
  font-family: var(--mono);
  font-size: 0.78rem;
  color: var(--brass-dim);
}

/* ---------- things to know: real platform mechanics, not invented policy ---------- */
.know-section {
  margin-top: 2.75rem;
  padding-top: 2rem;
  border-top: 1px solid var(--paper-line);
}
.know-list {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1.5rem;
}
.know-list dt {
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--paper-ink-soft);
  margin-bottom: 0.35rem;
}
.know-list dd {
  margin: 0;
  font-size: 0.92rem;
  line-height: 1.55;
  color: var(--paper-ink);
  max-width: 40ch;
}

@media (max-width: 720px) {
  .particulars {
    grid-template-columns: 1fr 1fr;
  }
  .room-main {
    grid-template-columns: 1fr;
    align-items: flex-start;
    gap: 0.6rem;
  }
  .room-price {
    text-align: left;
  }
  .know-list {
    grid-template-columns: 1fr;
  }
  .offers-list {
    grid-template-columns: 1fr;
  }
}
</style>
