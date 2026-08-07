<script setup lang="ts">
import { computed, watch } from 'vue'
import { X } from 'lucide-vue-next'
import { useAuthStore } from '@/stores/auth'
import { useCompanyStore } from '@/stores/company'
import type { CompanyUserStatus } from '@/types/companyUser'

const props = defineProps<{ open: boolean }>()
const emit = defineEmits<{ close: [] }>()

const auth = useAuthStore()
const company = useCompanyStore()

// The raw numeric ID is only useful for support/admin lookups, not something a regular
// guest needs to see about their own account.
const canSeeUserId = computed(() => auth.roles.includes('ADMIN') || auth.roles.includes('SUPPORT'))

const STATUS_CLASSES: Record<CompanyUserStatus, string> = {
  ACTIVE: 'text-champagne bg-champagne/10 border-champagne/25',
  INVITED: 'text-amber-300/90 bg-amber-300/10 border-amber-300/20',
  PENDING_APPROVAL: 'text-amber-300/90 bg-amber-300/10 border-amber-300/20',
  INACTIVE: 'text-bone-dim bg-bone/5 border-hairline',
  SUSPENDED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
  REMOVED: 'text-rose-300/90 bg-rose-300/10 border-rose-300/20',
}

watch(
  () => props.open,
  (isOpen) => {
    if (isOpen) document.body.style.overflow = 'hidden'
    else document.body.style.overflow = ''
  },
)
</script>

<template>
  <Teleport to="body">
    <Transition
      enter-active-class="transition duration-200 ease-out"
      enter-from-class="opacity-0"
      leave-active-class="transition duration-150 ease-in"
      leave-to-class="opacity-0"
    >
      <div
        v-if="open"
        class="fixed inset-0 z-50 bg-ink/70 backdrop-blur-sm flex items-center justify-center p-4"
        role="dialog"
        aria-modal="true"
        aria-labelledby="roles-modal-title"
        @click.self="emit('close')"
        @keydown.esc="emit('close')"
      >
        <div
          class="w-full max-w-md rounded-[1.75rem] bg-ink-2/90 backdrop-blur-2xl border border-hairline p-6 md:p-8 shadow-[0_40px_120px_-30px_rgba(0,0,0,0.9)]"
        >
          <div class="flex items-start justify-between gap-4 mb-6">
            <h2 id="roles-modal-title" class="font-display text-2xl text-bone">Your roles</h2>
            <button type="button" class="text-bone-dim hover:text-bone transition-colors" aria-label="Close" @click="emit('close')">
              <X class="w-5 h-5" aria-hidden="true" />
            </button>
          </div>

          <div class="flex flex-col gap-1 mb-6">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Account</span>
            <p class="text-sm font-light text-bone">{{ auth.email }}</p>
            <p v-if="canSeeUserId" class="text-xs font-light text-bone-dim">User ID {{ auth.userId }}</p>
            <div class="flex flex-wrap gap-2 mt-2">
              <span
                v-for="role in auth.roles"
                :key="role"
                class="px-2.5 py-1 rounded-full text-[11px] font-medium text-champagne bg-champagne/10 border border-champagne/25"
              >
                {{ role }}
              </span>
            </div>
          </div>

          <div class="flex flex-col gap-1">
            <span class="text-[11px] uppercase tracking-[0.12em] text-bone-dim">Company roles</span>

            <p v-if="!company.memberships.length" class="text-sm font-light text-bone-dim mt-2">
              Not staff on any property yet.
            </p>

            <ul v-else class="flex flex-col gap-2 mt-2">
              <li
                v-for="member in company.memberships"
                :key="member.id"
                class="flex items-center justify-between gap-3 px-3 py-2.5 rounded-lg bg-bone/5 border border-hairline"
              >
                <div class="min-w-0">
                  <p class="text-sm text-bone truncate">{{ member.companyName }}</p>
                  <p class="text-xs font-light text-bone-dim">Company ID {{ member.companyId }}</p>
                </div>
                <div class="flex flex-col items-end gap-1 shrink-0">
                  <span class="text-xs font-light text-bone-dim">{{ member.companyRole }}</span>
                  <span
                    class="px-2 py-0.5 rounded-full text-[10px] font-medium border"
                    :class="STATUS_CLASSES[member.status]"
                  >
                    {{ member.status }}
                  </span>
                </div>
              </li>
            </ul>
          </div>
        </div>
      </div>
    </Transition>
  </Teleport>
</template>
