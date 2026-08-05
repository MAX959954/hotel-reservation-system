<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRoute } from 'vue-router'
import { hotelsApi } from '../api/hotels'
import type { HotelResponse } from '../types/hotel'

const route = useRoute()
const { t } = useI18n()

const city = ref('')
const hotels = ref<HotelResponse[]>([])
const loading = ref(false)
const error = ref('')
const searched = ref(false)

// Same verified representative pool as the homepage's five-star grid — decorative
// stand-ins for entries with no imageUrl on file, never presented as that specific property.
const placeholderPhotos = [
  'https://images.unsplash.com/photo-1611892440504-42a792e24d32?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1566073771259-6a8506099945?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1590490360182-c33d57733427?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1582719478250-c89cae4dc85b?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1564501049412-61c2a3083791?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1520250497591-112f2f40a3f4?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1571896349842-33c89424de2d?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1618773928121-c32242e63f39?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1445019980597-93fa8acb246c?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1611048268330-53de574cae3b?w=800&q=80&auto=format&fit=crop',
  'https://images.unsplash.com/photo-1568084680786-a84f91d1153c?w=800&q=80&auto=format&fit=crop',
]

function photoFor(hotel: HotelResponse) {
  if (hotel.imageUrl) return hotel.imageUrl
  return placeholderPhotos[hotel.id % placeholderPhotos.length]
}

async function search() {
  if (!city.value.trim()) return
  loading.value = true
  error.value = ''
  searched.value = true
  try {
    hotels.value = await hotelsApi.getByCity(city.value.trim())
  } catch (err: any) {
    error.value = err.response?.data?.message ?? t('hotels.errorFallback')
    hotels.value = []
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  const queryCity = route.query.city
  if (typeof queryCity === 'string' && queryCity.trim()) {
    city.value = queryCity
    search()
  }
})
</script>

<template>
  <!--
    THESIS: search is a page you turn in the same register, not a separate utility screen —
    reuses the established Spine + ruled-line grammar from login/register (Folio III/IV)
    rather than inventing a new search-shell default, and results reuse the homepage's exact
    folio-card component so "five-star ledger" and "search results" read as one system.
    Extension of an established surface (new-work.md "Extend an existing surface"): no new
    palette, no new component, no concept tournament run.
    FOLIO: V — continues I/II (hero), III (login), IV (register).
  -->
  <div class="search-page">
    <aside class="spine" aria-hidden="true">
      <span class="spine-mark">Folio</span>
    </aside>

    <div class="search-content">
      <p class="folio-no">{{ t('hotels.folioFive') }}</p>
      <h1>{{ t('hotels.title') }}</h1>

      <form class="lookup" @submit.prevent="search">
        <label class="lookup-label" for="city-search">{{ t('hotels.cityLabel') }}</label>
        <div class="lookup-line">
          <input
            id="city-search"
            v-model="city"
            type="text"
            :placeholder="t('hotels.cityPlaceholder')"
            autocomplete="off"
            required
          />
          <button type="submit" class="brass-btn" :disabled="loading">
            {{ loading ? t('hotels.searching') : t('hotels.searchButton') }}
          </button>
        </div>
      </form>

      <p v-if="error" class="error">{{ error }}</p>
      <p v-else-if="searched && !loading && hotels.length === 0" class="empty-note">
        {{ t('hotels.noResults', { city }) }}
      </p>

      <ul v-if="hotels.length" class="folio-grid">
        <li v-for="(hotel, i) in hotels" :key="hotel.id" :style="{ '--stagger': i }">
          <router-link :to="`/hotels/${hotel.id}`" class="folio-card">
            <div class="folio-image">
              <img :src="photoFor(hotel)" :alt="hotel.name" loading="lazy" />
              <span class="folio-rating" aria-hidden="true">★ {{ hotel.startRating }}</span>
            </div>
            <div class="folio-body">
              <h3>{{ hotel.name }}</h3>
              <p class="folio-place">{{ hotel.city }}, {{ hotel.country }}</p>
              <p class="folio-company">{{ t('hotels.listedBy', { company: hotel.companyName }) }}</p>
            </div>
          </router-link>
        </li>
      </ul>
    </div>
  </div>
</template>

<style scoped>
.search-page {
  display: grid;
  grid-template-columns: minmax(96px, 16vw) 1fr;
  min-height: calc(100vh - 4.5rem);
}

.spine {
  border-right: 1px solid var(--brass-dim);
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 3rem;
}
.spine-mark {
  writing-mode: vertical-rl;
  font-family: var(--serif);
  font-style: italic;
  font-weight: 600;
  font-size: 1.15rem;
  letter-spacing: 0.02em;
  color: var(--brass-dim);
}

.search-content {
  padding: clamp(2.25rem, 5vw, 3.5rem) 2rem 4rem;
  max-width: 1000px;
  width: 100%;
  animation: settle 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
}
@keyframes settle {
  from {
    opacity: 0;
    transform: translateY(6px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.folio-no {
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--text-dim);
  margin: 0 0 0.5rem;
}

.search-content h1 {
  font-family: var(--serif);
  font-size: clamp(1.7rem, 3.2vw, 2.2rem);
  font-weight: 600;
  margin-bottom: 1.75rem;
}

.lookup {
  display: flex;
  flex-direction: column;
  gap: 0.5rem;
  max-width: 520px;
  margin-bottom: 1.5rem;
}
.lookup-label {
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.1em;
  text-transform: uppercase;
  color: var(--text-dim);
}
.lookup-line {
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  border-bottom: 1px solid var(--border);
  padding-bottom: 0.4rem;
  transition: border-color 0.15s ease;
}
.lookup-line:focus-within {
  border-bottom-color: var(--brass);
}
.lookup-line input {
  flex: 1;
  min-width: 0;
  border: none;
  background: transparent;
  font-family: var(--serif);
  font-style: italic;
  font-size: 1.15rem;
  color: var(--text-h);
  padding: 0.2rem 0;
}
.lookup-line input:focus-visible {
  outline: none;
}
.lookup-line input::placeholder {
  color: var(--text-dim);
  opacity: 0.6;
}

.brass-btn {
  font-family: var(--mono);
  font-size: 0.78rem;
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

.error {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--stamp-bright);
}
.empty-note {
  font-family: var(--mono);
  font-size: 0.85rem;
  color: var(--text-dim);
}

/* ---------- results: the exact folio-card grammar from the homepage ledger ---------- */
.folio-grid {
  list-style: none;
  margin: 2rem 0 0;
  padding: 0;
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(260px, 1fr));
  gap: 1.5rem;
}
.folio-grid > li {
  opacity: 0;
  animation: rise 0.5s cubic-bezier(0.16, 1, 0.3, 1) both;
  animation-delay: calc(var(--stagger, 0) * 70ms);
}
@keyframes rise {
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
  .search-content,
  .folio-grid > li {
    animation: none;
    opacity: 1;
  }
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
  position: relative;
  aspect-ratio: 4 / 3;
  background: var(--ink-cover);
  overflow: hidden;
}
.folio-image img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
  transition: transform 0.4s cubic-bezier(0.16, 1, 0.3, 1);
}
.folio-card:hover .folio-image img {
  transform: scale(1.05);
}
.folio-rating {
  position: absolute;
  top: 0.65rem;
  right: 0.65rem;
  font-family: var(--mono);
  font-size: 0.72rem;
  letter-spacing: 0.03em;
  color: var(--text-h);
  background: rgba(28, 19, 16, 0.72);
  border: 1px solid var(--brass-dim);
  border-radius: 999px;
  padding: 0.2rem 0.55rem;
  backdrop-filter: blur(2px);
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
.folio-company {
  font-family: var(--mono);
  font-size: 0.7rem;
  letter-spacing: 0.02em;
  color: var(--brass-dim);
  margin-top: 0.15rem;
}

@media (max-width: 720px) {
  .search-page {
    grid-template-columns: 1fr;
  }
  .spine {
    border-right: none;
    border-bottom: 1px solid var(--brass-dim);
    padding: 1.25rem 0;
    align-items: center;
  }
  .spine-mark {
    writing-mode: horizontal-tb;
    font-size: 1.05rem;
  }
  .search-content {
    padding: 2.5rem 1.5rem 3rem;
  }
}
</style>
