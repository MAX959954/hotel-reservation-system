<script setup lang="ts">
import { onMounted, ref } from 'vue'

/**
 * "Sign in with Google" via Google Identity Services. Renders Google's own button and
 * emits the ID token (credential) it returns; the backend verifies it at
 * POST /api/auth/google. Renders nothing when VITE_GOOGLE_CLIENT_ID isn't set, so local
 * setups without a Google OAuth client simply don't show the option.
 */
const emit = defineEmits<{ credential: [idToken: string] }>()

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
      <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">or</span>
      <span class="h-px flex-1 bg-hairline" />
    </div>
    <div ref="container" class="min-h-[44px]" />
  </div>
</template>
