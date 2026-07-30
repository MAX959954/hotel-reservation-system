<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { hotelsApi } from '../api/hotels'
import { useAuthStore } from '../stores/auth'
import type { HotelResponse } from '../types/hotel'

const router = useRouter()
const auth = useAuthStore()

const city = ref('')
const featured = ref<HotelResponse[]>([])
const featuredLoading = ref(true)
const featuredError = ref(false)

const quickCities = ['Paris', 'Rome', 'Tokyo', 'New York']

function goSearch(cityName: string) {
  const trimmed = cityName.trim()
  if (!trimmed) return
  router.push({ name: 'hotels', query: { city: trimmed } })
}

function onSubmit() {
  goSearch(city.value)
}

const initials = (name: string) => name.trim().charAt(0).toUpperCase() || '?'

const hasFeatured = computed(() => featured.value.length > 0)

onMounted(async () => {
  try {
    featured.value = await hotelsApi.getByRating(5)
  } catch {
    featuredError.value = true
  } finally {
    featuredLoading.value = false
  }
})
</script>

<template>
  <!--
    THESIS: the register itself is the interface — a city search is a lookup in an open
    ledger, not a hero photo behind a dropdown; refuses the OTA glossy-hero default.
    OWN-WORLD: near-black leather/wood ground, brass as the committed accent, ivory
    ledger paper only inside bounded page panels, oxblood ink-stamps reserved for status.
    Spectral for voice, Courier Prime for ledger data, labels, and controls.
    STORY: a traveler sees the register is genuinely shared across independent houses,
    looks up a city, and reserves with the same confidence as handing over a passport.
    FIRST VIEWPORT: an open two-page ledger spread — left page states the pitch, right
    page holds a ruled city-search line ending in an ink-stamp submit; brass index tabs
    beneath the spine offer quick cities.
    FORM: own grounded direction, candidate 4 of 7 (guest-register ledger), seed 7031d284.
    FINISH: unreviewed and undocumented is unfinished; this build ends with the finish
    review, the verdict, and DESIGN.md.
  -->
  <div class="home">
    <section class="hero">
      <div class="ledger">
        <div class="spine" aria-hidden="true">
          <span class="clasp clasp-top" />
          <span class="clasp clasp-bottom" />
        </div>

        <div class="page page-left">
          <p class="folio-no">Folio&nbsp;I</p>
          <h1>Every stay,<br />entered by hand.</h1>
          <p class="pitch">
            Folio keeps one open register across hundreds of independently run hotels.
            No single brand behind the desk — just every house that chose to be listed,
            side by side, at its own rate.
          </p>
        </div>

        <div class="page page-right">
          <p class="folio-no">Folio&nbsp;II</p>
          <form class="lookup" @submit.prevent="onSubmit">
            <label class="lookup-label" for="city-search">Entered under city</label>
            <div class="lookup-line">
              <input
                id="city-search"
                v-model="city"
                type="text"
                placeholder="e.g. Lisbon"
                autocomplete="off"
                required
              />
              <button type="submit" class="stamp-btn">Search the register</button>
            </div>
          </form>

          <div class="quick-tabs">
            <span class="quick-label">Try</span>
            <button
              v-for="c in quickCities"
              :key="c"
              type="button"
              class="quick-tab"
              @click="goSearch(c)"
            >
              {{ c }}
            </button>
          </div>
        </div>
      </div>
    </section>

    <section class="section">
      <div class="section-head">
        <h2>The five-star ledger</h2>
        <p class="section-sub">Genuine top-rated entries, pulled live from the register.</p>
      </div>

      <p v-if="featuredLoading" class="loading-note">Turning to that page…</p>
      <p v-else-if="featuredError" class="empty-note">
        The register couldn't be reached just now. Search a city above once the desk is back.
      </p>
      <p v-else-if="!hasFeatured" class="empty-note">
        No five-star entries on file yet — search a city above to see who's in the register.
      </p>

      <ul v-else class="folio-grid">
        <li v-for="hotel in featured" :key="hotel.id">
          <router-link :to="`/hotels/${hotel.id}`" class="folio-card">
            <div class="folio-image">
              <img v-if="hotel.imageUrl" :src="hotel.imageUrl" :alt="hotel.name" loading="lazy" />
              <div v-else class="folio-blank" aria-hidden="true">
                <span>{{ initials(hotel.name) }}</span>
              </div>
            </div>
            <div class="folio-body">
              <h3>{{ hotel.name }}</h3>
              <p class="folio-place">{{ hotel.city }}, {{ hotel.country }}</p>
              <p class="folio-stars">{{ '★'.repeat(hotel.startRating) }}</p>
            </div>
          </router-link>
        </li>
      </ul>
    </section>

    <section class="section section-tint">
      <div class="section-head">
        <h2>One register, many houses</h2>
        <p class="section-sub">
          Every entry belongs to a different proprietor — independent hotels and small
          chains, not one brand wearing many names. Compare rating, address, and rate
          per night before you commit to a folio.
        </p>
      </div>
    </section>

    <section class="section">
      <div class="section-head">
        <h2>How the register works</h2>
      </div>

      <div class="steps">
        <div class="step">
          <span class="ink-stamp">Searched</span>
          <p>Look up a city and see who's entered under it.</p>
        </div>
        <div class="step">
          <span class="ink-stamp">Reserved</span>
          <p>Choose your room and dates, right on the folio.</p>
        </div>
        <div class="step">
          <span class="ink-stamp">Confirmed</span>
          <p>Your stay is inked in — cancel anytime before check-in.</p>
        </div>
      </div>
    </section>

    <section class="section section-cta">
      <template v-if="!auth.isAuthenticated">
        <h2>Add your name to the register.</h2>
        <p class="section-sub">Booking a stay means keeping your own page in the ledger.</p>
        <div class="cta-row">
          <router-link to="/register" class="stamp-btn">Register</router-link>
          <router-link to="/login" class="cta-secondary">Already have a page? Log in</router-link>
        </div>
      </template>
      <template v-else>
        <h2>Your ledger is open, {{ auth.email }}.</h2>
        <div class="cta-row">
          <router-link to="/bookings" class="stamp-btn">Go to my bookings</router-link>
        </div>
      </template>
    </section>

    <footer class="desk-footer">
      <span class="brass-rule" aria-hidden="true" />
      <p>Folio — a guest register for independently run hotels.</p>
    </footer>
  </div>
</template>

<style scoped>
.home {
  max-width: 1100px;
  margin: 0 auto;
  padding: 0 1.25rem 4rem;
}

/* ---------- hero ledger spread ---------- */
.hero {
  padding: clamp(2.5rem, 6vw, 4.5rem) 0 3rem;
  display: flex;
  justify-content: center;
}

.ledger {
  position: relative;
  display: grid;
  grid-template-columns: 1fr 1fr;
  width: 100%;
  max-width: 980px;
  background: var(--paper);
  border-radius: 6px;
  box-shadow:
    0 30px 60px -30px rgba(0, 0, 0, 0.65),
    0 2px 0 rgba(0, 0, 0, 0.25);
  animation: open-book 0.7s cubic-bezier(0.16, 1, 0.3, 1) both;
  transform-origin: 50% 50%;
}

@keyframes open-book {
  from {
    opacity: 0;
    transform: scale(0.97) rotateX(2deg);
  }
  to {
    opacity: 1;
    transform: scale(1) rotateX(0deg);
  }
}

@media (prefers-reduced-motion: reduce) {
  .ledger {
    animation: none;
  }
}

.page {
  padding: clamp(1.75rem, 4vw, 3rem);
  background-image: repeating-linear-gradient(
    var(--paper) 0px,
    var(--paper) 27px,
    var(--paper-line) 28px
  );
  background-position-y: 6px;
  color: var(--paper-ink);
  display: flex;
  flex-direction: column;
  gap: 1.1rem;
}
.page-left {
  border-radius: 6px 0 0 6px;
}
.page-right {
  border-radius: 0 6px 6px 0;
}

.spine {
  position: absolute;
  left: 50%;
  top: 0;
  bottom: 0;
  width: 14px;
  transform: translateX(-50%);
  background: linear-gradient(90deg, rgba(0, 0, 0, 0.18), var(--brass-dim), rgba(0, 0, 0, 0.18));
  z-index: 2;
}
.clasp {
  position: absolute;
  left: 50%;
  width: 22px;
  height: 22px;
  border-radius: 50%;
  background: radial-gradient(circle at 35% 30%, var(--brass-bright), var(--brass-dim));
  transform: translateX(-50%);
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.4);
}
.clasp-top {
  top: -11px;
}
.clasp-bottom {
  bottom: -11px;
}

.folio-no {
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--paper-ink-soft);
  margin: 0;
}

.page-left h1 {
  font-family: var(--serif);
  font-size: clamp(1.8rem, 3.6vw, 2.5rem);
  line-height: 1.15;
  color: var(--paper-ink);
  font-weight: 600;
}
.pitch {
  font-size: 0.98rem;
  color: var(--paper-ink-soft);
  max-width: 40ch;
}

.lookup {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  margin-top: 0.5rem;
}
.lookup-label {
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--paper-ink-soft);
}
.lookup-line {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  border-bottom: 1px solid var(--paper-ink-soft);
  padding-bottom: 0.4rem;
}
.lookup-line input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font-family: var(--serif);
  font-style: italic;
  font-size: 1.15rem;
  color: var(--paper-ink);
  padding: 0.2rem 0;
}
.lookup-line input:focus {
  outline: none;
}
.lookup-line input::placeholder {
  color: var(--paper-ink-soft);
  opacity: 0.6;
}

.stamp-btn {
  font-family: var(--mono);
  font-size: 0.78rem;
  letter-spacing: 0.05em;
  text-transform: uppercase;
  white-space: nowrap;
  color: var(--stamp);
  background: transparent;
  border: 2px solid var(--stamp);
  border-radius: 3px;
  padding: 0.5rem 0.9rem;
  cursor: pointer;
  text-decoration: none;
  display: inline-flex;
  transform: rotate(-2deg);
  transition: background 0.15s ease, color 0.15s ease;
}
.stamp-btn:hover {
  background: var(--stamp);
  color: var(--paper);
}

.quick-tabs {
  display: flex;
  align-items: center;
  gap: 0.6rem;
  flex-wrap: wrap;
  margin-top: 0.25rem;
}
.quick-label {
  font-family: var(--mono);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--paper-ink-soft);
}
.quick-tab {
  font-family: var(--mono);
  font-size: 0.78rem;
  background: var(--paper-2);
  border: 1px solid var(--paper-line);
  color: var(--paper-ink);
  padding: 0.25rem 0.6rem;
  border-radius: 999px;
  cursor: pointer;
  transition: border-color 0.15s ease, background 0.15s ease;
}
.quick-tab:hover {
  border-color: var(--brass-dim);
  background: var(--paper);
}

/* ---------- sections ---------- */
.section {
  padding: clamp(2.5rem, 5vw, 3.5rem) 0;
  border-top: 1px solid var(--border);
}
.section-tint {
  background: linear-gradient(180deg, transparent, rgba(201, 154, 75, 0.05), transparent);
}
.section-head {
  max-width: 60ch;
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  margin-bottom: 2rem;
}
.section-head h2 {
  font-family: var(--serif);
  font-size: clamp(1.4rem, 2.6vw, 1.9rem);
}
.section-sub {
  color: var(--text-dim);
  max-width: 55ch;
}

.loading-note,
.empty-note {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--text-dim);
}

/* ---------- five-star folio grid ---------- */
.folio-grid {
  list-style: none;
  margin: 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 1.25rem;
}
.folio-card {
  display: flex;
  flex-direction: column;
  text-decoration: none;
  color: inherit;
  background: var(--ink-raised);
  border: 1px solid var(--border);
  border-radius: 6px;
  overflow: hidden;
  transition: border-color 0.15s ease, transform 0.15s ease;
}
.folio-card:hover {
  border-color: var(--brass-dim);
  transform: translateY(-2px);
}
.folio-image {
  aspect-ratio: 4 / 3;
  background: var(--ink-cover);
}
.folio-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}
.folio-blank {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background-image: repeating-linear-gradient(
    var(--paper) 0px,
    var(--paper) 20px,
    var(--paper-line) 21px
  );
}
.folio-blank span {
  font-family: var(--serif);
  font-style: italic;
  font-size: 2.4rem;
  color: var(--brass-dim);
}
.folio-body {
  padding: 0.9rem 1rem 1.1rem;
  display: flex;
  flex-direction: column;
  gap: 0.2rem;
}
.folio-body h3 {
  font-family: var(--serif);
  font-size: 1.05rem;
  color: var(--text-h);
}
.folio-place {
  font-family: var(--mono);
  font-size: 0.78rem;
  color: var(--text-dim);
}
.folio-stars {
  color: var(--brass);
  font-size: 0.85rem;
  margin-top: 0.15rem;
}

/* ---------- how it works ---------- */
.steps {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 1.75rem;
}
.step {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 0.75rem;
}
.step p {
  color: var(--text-dim);
  font-size: 0.95rem;
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
.step:nth-child(2) .ink-stamp {
  transform: rotate(2deg);
}
.step:nth-child(3) .ink-stamp {
  transform: rotate(-1.5deg);
}

/* ---------- cta ---------- */
.section-cta {
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}
.section-cta h2 {
  font-family: var(--serif);
  font-size: clamp(1.5rem, 3vw, 2.1rem);
}
.cta-row {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  margin-top: 0.5rem;
  flex-wrap: wrap;
}
.cta-row .stamp-btn {
  transform: none;
}
.cta-secondary {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--text-dim);
}

/* ---------- footer ---------- */
.desk-footer {
  padding-top: 2.5rem;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 1rem;
  text-align: center;
}
.brass-rule {
  width: 60px;
  height: 2px;
  background: var(--brass-dim);
}
.desk-footer p {
  font-family: var(--mono);
  font-size: 0.75rem;
  color: var(--text-dim);
}

/* ---------- responsive ---------- */
@media (max-width: 720px) {
  .ledger {
    grid-template-columns: 1fr;
  }
  .spine {
    display: none;
  }
  .page-left,
  .page-right {
    border-radius: 0;
  }
  .page-left {
    border-radius: 6px 6px 0 0;
  }
  .page-right {
    border-radius: 0 0 6px 6px;
  }
  .steps {
    grid-template-columns: 1fr;
  }
}
</style>
