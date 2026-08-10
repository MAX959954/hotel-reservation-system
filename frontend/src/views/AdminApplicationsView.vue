<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { Check, FileText, Loader2, RotateCw, X } from 'lucide-vue-next'
import ExtranetShell from '@/components/ExtranetShell.vue'
import SiteFooter from '@/components/SiteFooter.vue'
import { companiesApi } from '@/api/companies'
import { apiErrorMessage, resolveUploadUrl } from '@/api/http'
import type { CompanyDocumentResponse, CompanyResponse } from '@/types/company'

const { t } = useI18n()

const applications = ref<CompanyResponse[]>([])
const documentsByCompany = ref<Record<number, CompanyDocumentResponse[]>>({})
const loading = ref(true)
const error = ref('')
const acting = ref<number | null>(null)
const rejectingId = ref<number | null>(null)
const rejectReason = ref('')

async function load() {
  loading.value = true
  error.value = ''
  try {
    applications.value = await companiesApi.getByStatus('PENDING_VERIFICATION')
    const docLists = await Promise.all(applications.value.map((c) => companiesApi.getDocuments(c.id)))
    documentsByCompany.value = Object.fromEntries(applications.value.map((c, i) => [c.id, docLists[i]]))
  } catch (e) {
    error.value = apiErrorMessage(e, t('adminApplications.loadError'))
  } finally {
    loading.value = false
  }
}

async function approve(id: number) {
  acting.value = id
  error.value = ''
  try {
    await companiesApi.approve(id)
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e, t('adminApplications.actionError'))
  } finally {
    acting.value = null
  }
}

function startReject(id: number) {
  rejectingId.value = id
  rejectReason.value = ''
}

async function confirmReject() {
  if (rejectingId.value == null) return
  acting.value = rejectingId.value
  error.value = ''
  try {
    await companiesApi.reject(rejectingId.value, rejectReason.value.trim() || undefined)
    rejectingId.value = null
    await load()
  } catch (e) {
    error.value = apiErrorMessage(e, t('adminApplications.actionError'))
  } finally {
    acting.value = null
  }
}

onMounted(load)
</script>

<template>
  <div class="min-h-screen bg-ink flex flex-col">
    <ExtranetShell :badge="$t('adminApplications.badge')" />

    <main class="flex-1 px-6 md:px-10 py-10 max-w-4xl mx-auto w-full">
      <h1 class="font-display text-4xl md:text-5xl text-bone mb-8">{{ $t('adminApplications.title') }}</h1>

      <div v-if="loading" class="flex flex-col gap-3">
        <div v-for="i in 3" :key="i" class="h-32 rounded-[1.25rem] bg-ink-2 border border-hairline animate-pulse" />
      </div>

      <div v-else-if="error" class="rounded-[1.25rem] border border-hairline bg-ink-2 p-8 flex flex-col items-start gap-4">
        <p class="text-sm text-rose-300">{{ error }}</p>
        <button
          type="button"
          class="flex items-center gap-2 rounded-full bg-champagne text-ink px-5 py-2.5 text-sm font-medium hover:bg-champagne-bright transition-colors"
          @click="load"
        >
          <RotateCw class="w-4 h-4" aria-hidden="true" />
          {{ $t('adminApplications.retry') }}
        </button>
      </div>

      <p v-else-if="!applications.length" class="text-sm font-light text-bone-dim">
        {{ $t('adminApplications.empty') }}
      </p>

      <div v-else class="flex flex-col gap-4">
        <article
          v-for="company in applications"
          :key="company.id"
          class="rounded-[1.25rem] bg-ink-2 border border-hairline p-6"
        >
          <div class="flex flex-wrap items-start justify-between gap-4">
            <div class="min-w-0">
              <h2 class="font-display text-xl text-bone">{{ company.name }}</h2>
              <p class="text-sm font-light text-bone-dim mt-1">
                {{ company.legalName }} · {{ company.city }}, {{ company.country }}
              </p>
              <p class="text-sm font-light text-bone-dim mt-1">{{ company.email }} · {{ company.phone }}</p>
              <p v-if="company.bankAccountHolder || company.bankIban" class="text-xs font-light text-bone-dim/80 mt-2">
                {{ company.bankAccountHolder }} — {{ company.bankIban }}
              </p>

              <ul v-if="documentsByCompany[company.id]?.length" class="flex flex-col gap-1.5 mt-3">
                <li v-for="doc in documentsByCompany[company.id]" :key="doc.id">
                  <a
                    :href="resolveUploadUrl(doc.fileUrl)!"
                    target="_blank"
                    rel="noopener"
                    class="flex items-center gap-2 text-xs text-champagne hover:text-champagne-bright transition-colors w-fit"
                  >
                    <FileText class="w-3.5 h-3.5" aria-hidden="true" />
                    {{ doc.originalFilename }}
                  </a>
                </li>
              </ul>
              <p v-else class="text-xs font-light text-bone-dim/60 mt-3">{{ $t('adminApplications.noDocuments') }}</p>
            </div>

            <div class="flex items-center gap-2 shrink-0">
              <button
                type="button"
                :disabled="acting === company.id"
                class="flex items-center gap-2 rounded-full bg-champagne text-ink px-4 py-2 text-xs font-medium hover:bg-champagne-bright transition-colors disabled:opacity-50"
                @click="approve(company.id)"
              >
                <Loader2 v-if="acting === company.id" class="w-3.5 h-3.5 animate-spin" aria-hidden="true" />
                <Check v-else class="w-3.5 h-3.5" aria-hidden="true" />
                {{ $t('adminApplications.approve') }}
              </button>
              <button
                type="button"
                :disabled="acting === company.id"
                class="flex items-center gap-2 rounded-full border border-hairline text-bone-dim px-4 py-2 text-xs font-light hover:text-bone hover:border-rose-300/40 transition-colors disabled:opacity-50"
                @click="startReject(company.id)"
              >
                <X class="w-3.5 h-3.5" aria-hidden="true" />
                {{ $t('adminApplications.reject') }}
              </button>
            </div>
          </div>

          <div v-if="rejectingId === company.id" class="mt-4 pt-4 border-t border-hairline flex flex-wrap items-end gap-3">
            <label class="flex flex-col gap-1 flex-1 min-w-[220px]">
              <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('adminApplications.rejectReason') }}</span>
              <input
                v-model="rejectReason"
                type="text"
                class="bg-transparent border-b border-hairline focus:border-champagne outline-none text-sm text-bone font-light py-1.5 transition-colors"
              />
            </label>
            <button
              type="button"
              :disabled="acting === company.id"
              class="rounded-full bg-rose-400/90 text-ink px-4 py-2 text-xs font-medium hover:bg-rose-400 transition-colors disabled:opacity-50"
              @click="confirmReject"
            >
              {{ $t('adminApplications.confirmReject') }}
            </button>
            <button
              type="button"
              class="rounded-full border border-hairline text-bone-dim px-4 py-2 text-xs font-light hover:text-bone transition-colors"
              @click="rejectingId = null"
            >
              {{ $t('adminApplications.cancel') }}
            </button>
          </div>
        </article>
      </div>
    </main>

    <SiteFooter />
  </div>
</template>
