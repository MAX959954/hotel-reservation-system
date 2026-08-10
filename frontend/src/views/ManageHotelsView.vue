<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { BedDouble, ChevronDown, Loader2, Pencil, Plus, RotateCw } from 'lucide-vue-next'
import ExtranetShell from '@/components/ExtranetShell.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { companiesApi } from '@/api/companies'
import { hotelsApi } from '@/api/hotels'
import { roomsApi } from '@/api/rooms'
import { apiErrorMessage } from '@/api/http'
import { useAuthStore } from '@/stores/auth'
import { useCompanyStore } from '@/stores/company'
import { useCurrencyStore } from '@/stores/currency'
import { ALL_AMENITIES, amenityLabel, hotelStatusLabel, type CreateHotelRequest, type HotelResponse, type PropertyType } from '@/types/hotel'
import { ALL_ROOM_TYPES, roomTypeLabel, type CreateRoomRequest, type RoomResponse, type RoomType } from '@/types/room'

const { t } = useI18n()
const auth = useAuthStore()
const company = useCompanyStore()
const currency = useCurrencyStore()

// Same "OWNER/MANAGER of this company, or ADMIN" gate the backend enforces on
// POST/PATCH /api/hotels and /api/rooms — a RECEPTIONIST has no business here.
const hasGlobalAccess = computed(() => auth.hasRole('ADMIN'))
const allCompanies = ref<{ companyId: number; companyName: string }[]>([])
const myManagedCompanies = computed(() =>
  company.memberships
    .filter((m) => m.status === 'ACTIVE' && (m.companyRole === 'OWNER' || m.companyRole === 'MANAGER'))
    .map((m) => ({ companyId: m.companyId, companyName: m.companyName })),
)
const pickableCompanies = computed(() => (hasGlobalAccess.value ? allCompanies.value : myManagedCompanies.value))

const companyId = ref<number | null>(null)
const hotels = ref<HotelResponse[]>([])
const loading = ref(true)
const error = ref('')

async function loadHotels() {
  if (!companyId.value) return
  loading.value = true
  error.value = ''
  try {
    hotels.value = await hotelsApi.getByCompany(companyId.value)
  } catch (e) {
    error.value = apiErrorMessage(e, t('manageHotels.loadError'))
  } finally {
    loading.value = false
  }
}

// --- Add / edit hotel ---------------------------------------------------------------

const showHotelForm = ref(false)
const editingHotelId = ref<number | null>(null)
const hotelSubmitting = ref(false)
const hotelError = ref('')
const hotelForm = ref({
  name: '',
  city: '',
  country: '',
  address: '',
  rating: 5,
  phone: '',
  email: '',
  description: '',
  imageUrl: '',
  propertyType: 'HOTEL' as PropertyType,
  amenities: [] as string[],
})

const hotelFormEl = ref<HTMLElement | null>(null)

// The form renders above the hotel list, at the top of the page — with a long list,
// "Edit" on a card far down otherwise opens a form the click never visibly reacts to,
// since it's off-screen until you scroll all the way back up.
async function scrollToHotelForm() {
  await nextTick()
  hotelFormEl.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

function resetHotelForm() {
  hotelForm.value = {
    name: '', city: '', country: '', address: '', rating: 5, phone: '', email: '',
    description: '', imageUrl: '', propertyType: 'HOTEL', amenities: [],
  }
  hotelError.value = ''
}

function toggleAddHotel() {
  if (showHotelForm.value && editingHotelId.value === null) {
    showHotelForm.value = false
    return
  }
  editingHotelId.value = null
  resetHotelForm()
  showHotelForm.value = true
  scrollToHotelForm()
}

function startEditHotel(hotel: HotelResponse) {
  editingHotelId.value = hotel.id
  hotelForm.value = {
    name: hotel.name,
    city: hotel.city,
    country: hotel.country,
    address: hotel.address,
    rating: hotel.startRating,
    phone: hotel.phone ?? '',
    email: hotel.email ?? '',
    description: hotel.description ?? '',
    imageUrl: hotel.imageUrl ?? '',
    propertyType: hotel.propertyType,
    amenities: [...(hotel.amenities ?? [])],
  }
  hotelError.value = ''
  showHotelForm.value = true
  scrollToHotelForm()
}

function cancelHotelForm() {
  showHotelForm.value = false
  editingHotelId.value = null
}

function toggleAmenity(amenity: string) {
  const i = hotelForm.value.amenities.indexOf(amenity)
  if (i === -1) hotelForm.value.amenities.push(amenity)
  else hotelForm.value.amenities.splice(i, 1)
}

async function submitHotel() {
  if (!companyId.value || hotelSubmitting.value) return
  hotelSubmitting.value = true
  hotelError.value = ''
  try {
    const request: CreateHotelRequest = { ...hotelForm.value, companyId: companyId.value, amenities: hotelForm.value.amenities as never }
    if (editingHotelId.value) {
      await hotelsApi.update(editingHotelId.value, request)
      showHotelForm.value = false
      editingHotelId.value = null
      await loadHotels()
    } else {
      const created = await hotelsApi.create(request)
      showHotelForm.value = false
      resetHotelForm()
      await loadHotels()
      // Straight into "add rooms" for the property just created — a hotel with zero
      // rooms can't be booked, so this is the natural next step, not a separate errand.
      expandedHotelId.value = created.id
      roomsByHotel.value[created.id] = []
      openRoomForm(created.id)
    }
  } catch (e) {
    hotelError.value = apiErrorMessage(e, t('manageHotels.hotelSubmitError'))
  } finally {
    hotelSubmitting.value = false
  }
}

// --- Rooms per hotel --------------------------------------------------------------

const expandedHotelId = ref<number | null>(null)
const roomsByHotel = ref<Record<number, RoomResponse[]>>({})
const roomsLoading = ref<number | null>(null)
const showRoomForm = ref<number | null>(null)
const editingRoomId = ref<number | null>(null)
const roomSubmitting = ref(false)
const roomError = ref('')
const roomForm = ref({ number: '', type: 'DOUBLE' as RoomType, pricePerNight: 100, capacity: 2, floor: 1, description: '' })

async function toggleHotel(hotel: HotelResponse) {
  if (expandedHotelId.value === hotel.id) {
    expandedHotelId.value = null
    return
  }
  expandedHotelId.value = hotel.id
  if (roomsByHotel.value[hotel.id]) return
  roomsLoading.value = hotel.id
  try {
    roomsByHotel.value[hotel.id] = await roomsApi.getByHotel(hotel.id)
  } catch {
    roomsByHotel.value[hotel.id] = []
  } finally {
    roomsLoading.value = null
  }
}

function resetRoomForm() {
  roomForm.value = { number: '', type: 'DOUBLE', pricePerNight: 100, capacity: 2, floor: 1, description: '' }
  roomError.value = ''
}

function openRoomForm(hotelId: number) {
  showRoomForm.value = hotelId
  editingRoomId.value = null
  resetRoomForm()
}

function startEditRoom(hotelId: number, room: RoomResponse) {
  showRoomForm.value = hotelId
  editingRoomId.value = room.id
  roomForm.value = {
    number: room.number,
    type: room.type,
    pricePerNight: room.pricePerNight,
    capacity: room.capacity,
    floor: room.floor,
    description: room.description ?? '',
  }
  roomError.value = ''
}

function cancelRoomForm() {
  showRoomForm.value = null
  editingRoomId.value = null
}

async function submitRoom(hotelId: number) {
  if (roomSubmitting.value) return
  roomSubmitting.value = true
  roomError.value = ''
  try {
    const request: CreateRoomRequest = { ...roomForm.value, hotelId }
    if (editingRoomId.value) {
      await roomsApi.update(editingRoomId.value, request)
    } else {
      await roomsApi.create(request)
    }
    showRoomForm.value = null
    editingRoomId.value = null
    roomsByHotel.value[hotelId] = await roomsApi.getByHotel(hotelId)
  } catch (e) {
    roomError.value = apiErrorMessage(e, t('manageHotels.roomSubmitError'))
  } finally {
    roomSubmitting.value = false
  }
}

watch(pickableCompanies, (list) => {
  if (!companyId.value && list.length) companyId.value = list[0].companyId
})
watch(companyId, loadHotels)

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
    <ExtranetShell />

    <main class="flex-1 px-6 md:px-10 py-10 max-w-4xl mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-4">{{ $t('manageHotels.title') }}</h1>

      <div v-if="!pickableCompanies.length" class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8">
        <p class="text-sm font-light text-bone-dim">{{ $t('manageHotels.noCompanies') }}</p>
      </div>

      <template v-else>
        <div class="flex flex-wrap items-center gap-2 mb-6">
          <button
            v-for="c in pickableCompanies"
            :key="c.companyId"
            type="button"
            class="px-3 py-1.5 rounded-full text-xs font-light transition-colors border"
            :class="
              companyId === c.companyId
                ? 'bg-bone/8 text-bone border-hairline'
                : 'text-bone-dim border-transparent hover:text-bone hover:bg-bone/5'
            "
            @click="companyId = c.companyId"
          >
            {{ c.companyName }}
          </button>
        </div>

        <button
          type="button"
          class="flex items-center gap-2 rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors mb-6"
          @click="toggleAddHotel"
        >
          <Plus class="w-4 h-4" aria-hidden="true" />
          {{ $t('manageHotels.addHotel') }}
        </button>

        <form v-if="showHotelForm" ref="hotelFormEl" class="flex flex-col gap-4 rounded-[1.25rem] bg-ink-2 border border-hairline p-6 mb-8" @submit.prevent="submitHotel">
          <h2 class="font-display text-xl text-bone">
            {{ editingHotelId ? $t('manageHotels.editHotelTitle') : $t('manageHotels.addHotel') }}
          </h2>

          <div class="grid grid-cols-2 gap-4">
            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldName') }}</span>
              <input v-model="hotelForm.name" type="text" required class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldType') }}</span>
              <select v-model="hotelForm.propertyType" class="bg-ink border border-hairline rounded-full px-3 py-1.5 text-sm text-bone outline-none focus:border-champagne transition-colors">
                <option value="HOTEL">{{ $t('hotels.propertyHotel') }}</option>
                <option value="APARTMENT">{{ $t('hotels.propertyApartment') }}</option>
              </select>
            </label>
          </div>

          <div class="grid grid-cols-2 gap-4">
            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldCity') }}</span>
              <input v-model="hotelForm.city" type="text" required class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldCountry') }}</span>
              <input v-model="hotelForm.country" type="text" required class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
            </label>
          </div>

          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldAddress') }}</span>
            <input v-model="hotelForm.address" type="text" required class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
          </label>

          <div class="grid grid-cols-3 gap-4">
            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldRating') }}</span>
              <input v-model.number="hotelForm.rating" type="number" min="1" max="5" required class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldPhone') }}</span>
              <input v-model="hotelForm.phone" type="tel" required class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldEmail') }}</span>
              <input v-model="hotelForm.email" type="email" required class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
            </label>
          </div>

          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldImageUrl') }}</span>
            <input v-model="hotelForm.imageUrl" type="text" required placeholder="/images/hotels/hotel-01.jpg" class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone placeholder:text-bone-dim/50 font-light py-1.5 transition-colors" />
          </label>

          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldDescription') }}</span>
            <textarea v-model="hotelForm.description" required rows="3" class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors resize-none" />
          </label>

          <div class="flex flex-col gap-2">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.fieldAmenities') }}</span>
            <div class="flex flex-wrap gap-2">
              <button
                v-for="a in ALL_AMENITIES"
                :key="a"
                type="button"
                class="px-3 py-1.5 rounded-full text-xs font-light transition-colors border"
                :class="
                  hotelForm.amenities.includes(a)
                    ? 'bg-champagne text-ink border-champagne'
                    : 'text-bone-dim border-hairline hover:text-bone hover:border-champagne-dim'
                "
                @click="toggleAmenity(a)"
              >
                {{ amenityLabel(a) }}
              </button>
            </div>
          </div>

          <p v-if="hotelError" class="text-xs text-rose-300">{{ hotelError }}</p>

          <div class="flex items-center gap-3">
            <button
              type="submit"
              :disabled="hotelSubmitting"
              class="flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
            >
              <Loader2 v-if="hotelSubmitting" class="w-4 h-4 animate-spin" aria-hidden="true" />
              {{ editingHotelId ? $t('manageHotels.saveChanges') : $t('manageHotels.submitHotel') }}
            </button>
            <button
              v-if="editingHotelId"
              type="button"
              class="rounded-full border border-hairline text-bone-dim px-6 py-3 text-sm font-light hover:text-bone transition-colors"
              @click="cancelHotelForm"
            >
              {{ $t('manageHotels.cancel') }}
            </button>
          </div>
        </form>

        <div v-if="loading" class="flex flex-col gap-3">
          <div v-for="i in 2" :key="i" class="h-24 rounded-[1.25rem] bg-ink-2 border border-hairline animate-pulse" />
        </div>

        <div v-else-if="error" class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4">
          <p class="text-sm text-rose-300">{{ error }}</p>
          <button type="button" class="flex items-center gap-2 rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors" @click="loadHotels">
            <RotateCw class="w-4 h-4" aria-hidden="true" />
            {{ $t('manageHotels.retry') }}
          </button>
        </div>

        <p v-else-if="!hotels.length" class="text-sm font-light text-bone-dim">{{ $t('manageHotels.empty') }}</p>

        <div v-else class="flex flex-col gap-4">
          <article v-for="hotel in hotels" :key="hotel.id" class="rounded-[1.25rem] bg-ink-2 border border-hairline overflow-hidden">
            <div class="w-full flex items-center justify-between gap-4 p-6">
              <button type="button" class="flex-1 min-w-0 text-left" @click="toggleHotel(hotel)">
                <div class="flex items-center gap-3 flex-wrap">
                  <h2 class="font-display text-xl text-bone">{{ hotel.name }}</h2>
                  <span class="px-2.5 py-1 rounded-full text-[11px] font-medium border text-bone-dim border-hairline">
                    {{ hotelStatusLabel(hotel.status) }}
                  </span>
                </div>
                <p class="text-sm font-light text-bone-dim mt-1">{{ hotel.city }}, {{ hotel.country }}</p>
              </button>
              <button
                type="button"
                class="shrink-0 flex items-center gap-1.5 rounded-full border border-hairline text-bone-dim px-3 py-1.5 text-xs font-light hover:text-bone hover:border-champagne-dim transition-colors"
                @click="startEditHotel(hotel)"
              >
                <Pencil class="w-3.5 h-3.5" aria-hidden="true" />
                {{ $t('manageHotels.edit') }}
              </button>
              <button type="button" class="shrink-0" :aria-label="$t('manageHotels.toggleRooms')" @click="toggleHotel(hotel)">
                <ChevronDown class="w-4 h-4 text-bone-dim transition-transform" :class="{ 'rotate-180': expandedHotelId === hotel.id }" aria-hidden="true" />
              </button>
            </div>

            <div v-if="expandedHotelId === hotel.id" class="border-t border-hairline p-6 pt-4">
              <div v-if="roomsLoading === hotel.id" class="flex flex-col gap-2">
                <div v-for="i in 2" :key="i" class="h-12 rounded-xl bg-ink border border-hairline animate-pulse" />
              </div>

              <template v-else>
                <ul v-if="roomsByHotel[hotel.id]?.length" class="flex flex-col gap-2 mb-4">
                  <li v-for="room in roomsByHotel[hotel.id]" :key="room.id" class="flex items-center justify-between gap-3 rounded-xl bg-ink border border-hairline px-4 py-3">
                    <span class="flex items-center gap-2 text-sm text-bone">
                      <BedDouble class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
                      {{ $t('hotelDetail.roomInfo', { number: room.number, floor: room.floor, capacity: room.capacity }) }}
                    </span>
                    <span class="flex items-center gap-2 shrink-0">
                      <span class="text-xs font-light text-bone-dim">{{ roomTypeLabel(room.type) }}</span>
                      <span class="text-sm text-bone">{{ currency.format(room.pricePerNight) }}</span>
                      <button
                        type="button"
                        class="flex items-center gap-1 rounded-full border border-hairline text-bone-dim px-2.5 py-1 text-[11px] font-light hover:text-bone hover:border-champagne-dim transition-colors"
                        @click="startEditRoom(hotel.id, room)"
                      >
                        <Pencil class="w-3 h-3" aria-hidden="true" />
                        {{ $t('manageHotels.edit') }}
                      </button>
                    </span>
                  </li>
                </ul>
                <p v-else class="text-sm font-light text-bone-dim mb-4">{{ $t('manageHotels.noRooms') }}</p>

                <button
                  v-if="showRoomForm !== hotel.id"
                  type="button"
                  class="flex items-center gap-2 rounded-full border border-hairline text-bone px-4 py-2 text-xs font-light hover:border-champagne-dim transition-colors"
                  @click="openRoomForm(hotel.id)"
                >
                  <Plus class="w-3.5 h-3.5" aria-hidden="true" />
                  {{ $t('manageHotels.addRoom') }}
                </button>

                <form v-else class="flex flex-wrap items-end gap-3 mt-2" @submit.prevent="submitRoom(hotel.id)">
                  <label class="flex flex-col gap-1">
                    <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.roomNumber') }}</span>
                    <input v-model="roomForm.number" type="text" required class="w-24 bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.roomType') }}</span>
                    <select v-model="roomForm.type" class="bg-ink border border-hairline rounded-full px-3 py-1.5 text-sm text-bone outline-none focus:border-champagne transition-colors">
                      <option v-for="rt in ALL_ROOM_TYPES" :key="rt" :value="rt">{{ roomTypeLabel(rt) }}</option>
                    </select>
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.roomPrice') }}</span>
                    <input v-model.number="roomForm.pricePerNight" type="number" min="0" step="0.01" required class="w-24 bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.roomCapacity') }}</span>
                    <input v-model.number="roomForm.capacity" type="number" min="1" required class="w-16 bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
                  </label>
                  <label class="flex flex-col gap-1">
                    <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('manageHotels.roomFloor') }}</span>
                    <input v-model.number="roomForm.floor" type="number" required class="w-16 bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors" />
                  </label>
                  <button
                    type="submit"
                    :disabled="roomSubmitting"
                    class="flex items-center gap-2 rounded-full bg-champagne text-ink px-4 py-2 text-xs font-medium hover:bg-champagne-bright transition-colors disabled:opacity-50"
                  >
                    <Loader2 v-if="roomSubmitting" class="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                    {{ editingRoomId ? $t('manageHotels.saveChanges') : $t('manageHotels.submitRoom') }}
                  </button>
                  <button type="button" class="rounded-full border border-hairline text-bone-dim px-4 py-2 text-xs font-light hover:text-bone transition-colors" @click="cancelRoomForm">
                    {{ $t('manageHotels.cancel') }}
                  </button>
                </form>
                <p v-if="roomError" class="text-xs text-rose-300 mt-2">{{ roomError }}</p>
              </template>
            </div>
          </article>
        </div>
      </template>
    </main>

    <SiteFooter />
  </div>
</template>
