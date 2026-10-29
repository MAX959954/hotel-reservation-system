<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

/**
 * "Sign in with Google" via Google Identity Services. Renders Google's own button and
 * emits the ID token (credential) it returns; the backend verifies it at
 * POST /api/auth/google. Renders nothing when VITE_GOOGLE_CLIENT_ID isn't set, so local
 * setups without a Google OAuth client simply don't show the option.
 */
const emit = defineEmits<{ credential: [idToken: string] }>()
const { locale } = useI18n()

const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID ?? ''
const container = ref<HTMLDivElement | null>(null)
const failed = ref(false)

const GIS_SRC = 'https://accounts.google.com/gsi/client'

interface GoogleId {
  initialize(config: { client_id: string; callback: (r: { credential: string }) => void }): void
  renderButton(el: HTMLElement, options: Record<string, unknown>): void
}
type GoogleWindow = Window & { google?: { accounts: { id: GoogleId } } }

let scriptPromise: Promise<void> | null = null
function loadScript(): Promise<void> {
  if ((window as GoogleWindow).google?.accounts?.id) return Promise.resolve()
  if (!scriptPromise) {
    scriptPromise = new Promise((resolve, reject) => {
      const s = document.createElement('script')
      s.src = GIS_SRC
      s.async = true
      s.defer = true
      s.onload = () => resolve()
      s.onerror = () => {
        scriptPromise = null
        reject(new Error('Could not load Google sign-in'))
      }
      document.head.appendChild(s)
    })
  }
  return scriptPromise
}

onMounted(async () => {
  if (!clientId || !container.value) return
  try {
    await loadScript()
    const id = (window as GoogleWindow).google!.accounts.id
    id.initialize({ client_id: clientId, callback: (r) => emit('credential', r.credential) })
    id.renderButton(container.value, {
      theme: 'filled_black',
      size: 'large',
      shape: 'pill',
      text: 'continue_with',
      width: 320,
      // Google's button text follows this, not the page language.
      locale: locale.value,
    })
  } catch {
    failed.value = true
  }
})
</script>

<template>
  <div v-if="clientId && !failed" class="flex flex-col items-center gap-3">
    <div class="flex items-center gap-3 w-full">
      <span class="h-px flex-1 bg-hairline" />
      <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">{{ $t('auth.or') }}</span>
      <span class="h-px flex-1 bg-hairline" />
    </div>
    <div ref="container" class="gsi-container min-h-[44px]" />
  </div>
</template>

<style scoped>
/* The page sets `color-scheme: dark` (style.css). Google's button is a cross-origin
   iframe whose document is light, and a color-scheme mismatch makes the browser paint
   the iframe with an opaque white backdrop — the white rectangle around the pill.
   Matching the iframe's scheme keeps its background transparent. */
.gsi-container :deep(iframe) {
  color-scheme: light;
}
</style>
