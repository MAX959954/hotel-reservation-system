<script setup lang="ts">
import { onMounted, onUnmounted } from 'vue'
import AuthModal from '@/components/AuthModal.vue'
import SettingsModal from '@/components/SettingsModal.vue'
import { destroySmoothScroll, initSmoothScroll } from '@/lib/motion'

onMounted(() => {
  initSmoothScroll()
})

onUnmounted(() => {
  destroySmoothScroll()
})
</script>

<template>
  <main class="min-h-screen bg-ink">
    <RouterView />
    <AuthModal />
    <SettingsModal />
  </main>
</template>
