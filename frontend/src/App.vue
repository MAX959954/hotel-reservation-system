<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'

const auth = useAuthStore()
const router = useRouter()

function onLogout() {
  auth.logout()
  router.push('/login')
}
</script>

<template>
  <header class="nav">
    <router-link to="/hotels" class="brand">Hotel Reservations</router-link>
    <nav>
      <router-link to="/hotels">Hotels</router-link>
      <router-link v-if="auth.isAuthenticated" to="/bookings">My bookings</router-link>
      <template v-if="auth.isAuthenticated">
        <span class="email">{{ auth.email }}</span>
        <button @click="onLogout">Log out</button>
      </template>
      <template v-else>
        <router-link to="/login">Log in</router-link>
        <router-link to="/register">Register</router-link>
      </template>
    </nav>
  </header>

  <main>
    <router-view />
  </main>
</template>

<style scoped>
.nav {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 1rem 1.5rem;
  border-bottom: 1px solid #ddd;
}
.brand {
  font-weight: 600;
  text-decoration: none;
  color: inherit;
}
nav {
  display: flex;
  align-items: center;
  gap: 1rem;
}
.email {
  color: #555;
  font-size: 0.9rem;
}
button {
  cursor: pointer;
}
</style>
