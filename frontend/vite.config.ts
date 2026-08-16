import { fileURLToPath, URL } from 'node:url'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import tailwindcss from '@tailwindcss/vite'

export default defineConfig({
  plugins: [vue(), tailwindcss()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    // Vite rejects requests whose Host header it doesn't recognize (protection against
    // DNS rebinding). A Cloudflare quick tunnel forwards the real Host header through,
    // and trycloudflare.com's subdomain is random per run, so it's allow-listed by
    // suffix rather than by exact hostname.
    allowedHosts: ['.trycloudflare.com'],
  },
})
