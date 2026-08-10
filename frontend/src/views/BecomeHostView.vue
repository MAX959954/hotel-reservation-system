<script setup lang="ts">
import { ref } from 'vue'
import { CheckCircle2, FileText, Loader2, UploadCloud } from 'lucide-vue-next'
import Navbar from '@/components/Navbar.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { companiesApi } from '@/api/companies'
import { apiErrorMessage } from '@/api/http'
import type { CompanyApplicationRequest, CompanyDocumentResponse } from '@/types/company'

type Step = 'form' | 'documents' | 'done'
const step = ref<Step>('form')

const form = ref<CompanyApplicationRequest>({
  name: '',
  legalName: '',
  email: '',
  phone: '',
  address: '',
  city: '',
  country: '',
  webSite: '',
  bankAccountHolder: '',
  bankIban: '',
})

const submitting = ref(false)
const submitError = ref('')
const companyId = ref<number | null>(null)

const documents = ref<CompanyDocumentResponse[]>([])
const uploadInput = ref<HTMLInputElement | null>(null)
const uploading = ref(false)
const uploadError = ref('')

async function submit() {
  if (submitting.value) return
  submitting.value = true
  submitError.value = ''
  try {
    const company = await companiesApi.create(form.value)
    companyId.value = company.id
    step.value = 'documents'
  } catch (e) {
    submitError.value = apiErrorMessage(e)
  } finally {
    submitting.value = false
  }
}

function pickDocument() {
  uploadInput.value?.click()
}

async function onDocumentChange(event: Event) {
  const file = (event.target as HTMLInputElement).files?.[0]
  if (!file || !companyId.value) return
  uploading.value = true
  uploadError.value = ''
  try {
    documents.value.push(await companiesApi.uploadDocument(companyId.value, file))
  } catch (e) {
    uploadError.value = apiErrorMessage(e)
  } finally {
    uploading.value = false
    if (uploadInput.value) uploadInput.value.value = ''
  }
}
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <div class="border-b border-hairline">
      <Navbar />
    </div>

    <main class="flex-1 px-6 md:px-10 py-10 max-w-2xl mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-3">{{ $t('becomeHost.title') }}</h1>
      <p class="text-sm font-light text-bone-dim mb-8">{{ $t('becomeHost.subtitle') }}</p>

      <!-- Step 1: company details -->
      <form v-if="step === 'form'" class="flex flex-col gap-4" @submit.prevent="submit">
        <label class="flex flex-col gap-1">
          <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldName') }}</span>
          <input
            v-model="form.name"
            type="text"
            required
            class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
          />
        </label>

        <label class="flex flex-col gap-1">
          <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldLegalName') }}</span>
          <input
            v-model="form.legalName"
            type="text"
            required
            class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
          />
        </label>

        <div class="grid grid-cols-2 gap-4">
          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldEmail') }}</span>
            <input
              v-model="form.email"
              type="email"
              required
              class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldPhone') }}</span>
            <input
              v-model="form.phone"
              type="tel"
              required
              class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
            />
          </label>
        </div>

        <label class="flex flex-col gap-1">
          <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldAddress') }}</span>
          <input
            v-model="form.address"
            type="text"
            required
            class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
          />
        </label>

        <div class="grid grid-cols-2 gap-4">
          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldCity') }}</span>
            <input
              v-model="form.city"
              type="text"
              required
              class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldCountry') }}</span>
            <input
              v-model="form.country"
              type="text"
              required
              class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
            />
          </label>
        </div>

        <label class="flex flex-col gap-1">
          <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldWebsite') }}</span>
          <input
            v-model="form.webSite"
            type="url"
            required
            placeholder="https://"
            class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone placeholder:text-bone-dim/50 font-light py-1.5 transition-colors"
          />
        </label>

        <div class="grid grid-cols-2 gap-4">
          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldBankHolder') }}</span>
            <input
              v-model="form.bankAccountHolder"
              type="text"
              class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
            />
          </label>
          <label class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('becomeHost.fieldIban') }}</span>
            <input
              v-model="form.bankIban"
              type="text"
              class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
            />
          </label>
        </div>

        <p v-if="submitError" class="text-xs text-rose-300">{{ submitError }}</p>

        <button
          type="submit"
          :disabled="submitting"
          class="mt-2 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors disabled:opacity-40 disabled:cursor-not-allowed"
        >
          <Loader2 v-if="submitting" class="w-4 h-4 animate-spin" aria-hidden="true" />
          {{ $t('becomeHost.submit') }}
        </button>
      </form>

      <!-- Step 2: supporting documents -->
      <div v-else-if="step === 'documents'" class="flex flex-col gap-4">
        <p class="text-sm font-light text-bone-dim">{{ $t('becomeHost.documentsHint') }}</p>

        <input ref="uploadInput" type="file" accept="application/pdf,image/jpeg,image/png" class="hidden" @change="onDocumentChange" />

        <ul v-if="documents.length" class="flex flex-col gap-2">
          <li
            v-for="doc in documents"
            :key="doc.id"
            class="flex items-center gap-3 rounded-xl bg-ink-2 border border-hairline px-4 py-3"
          >
            <FileText class="w-4 h-4 text-champagne shrink-0" aria-hidden="true" />
            <span class="text-sm text-bone truncate">{{ doc.originalFilename }}</span>
          </li>
        </ul>

        <button
          type="button"
          :disabled="uploading"
          class="flex items-center justify-center gap-2 rounded-full border border-hairline text-bone px-5 py-2.5 text-sm font-light hover:border-champagne-dim transition-colors disabled:opacity-50"
          @click="pickDocument"
        >
          <Loader2 v-if="uploading" class="w-4 h-4 animate-spin" aria-hidden="true" />
          <UploadCloud v-else class="w-4 h-4" aria-hidden="true" />
          {{ $t('becomeHost.uploadButton') }}
        </button>
        <p v-if="uploadError" class="text-xs text-rose-300">{{ uploadError }}</p>

        <button
          type="button"
          class="mt-2 flex items-center justify-center gap-2 rounded-full bg-champagne text-ink px-6 py-3 text-sm font-medium hover:bg-champagne-bright transition-colors"
          @click="step = 'done'"
        >
          {{ $t('becomeHost.finish') }}
        </button>
      </div>

      <!-- Step 3: confirmation -->
      <div v-else class="flex flex-col items-center gap-4 text-center py-8">
        <span class="w-14 h-14 rounded-full bg-champagne/10 border border-champagne/25 flex items-center justify-center">
          <CheckCircle2 class="w-7 h-7 text-champagne" aria-hidden="true" />
        </span>
        <h2 class="font-display text-2xl text-bone">{{ $t('becomeHost.doneTitle') }}</h2>
        <p class="text-sm font-light text-bone-dim max-w-sm">{{ $t('becomeHost.doneMessage') }}</p>
      </div>
    </main>

    <SiteFooter />
  </div>
</template>
