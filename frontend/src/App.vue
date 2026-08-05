<script setup lang="ts">
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import { useAuthStore } from './stores/auth'
import { useAuthModalStore } from './stores/authModal'
import { SUPPORTED_LOCALES, setLocale, type LocaleCode } from './i18n'
import { SUPPORTED_CURRENCIES, useCurrencyStore, type CurrencyCode } from './stores/currency'
import LedgerSelect from './components/LedgerSelect.vue'
import AuthModal from './components/AuthModal.vue'

const auth = useAuthStore()
const authModal = useAuthModalStore()
const router = useRouter()
const { t, locale } = useI18n()
const currency = useCurrencyStore()

function onLogout() {
  auth.logout()
  router.push('/')
}

const localeOptions = SUPPORTED_LOCALES.map((l) => ({ value: l.code, label: l.code.toUpperCase() }))
const currencyOptions = SUPPORTED_CURRENCIES.map((c) => ({ value: c.code, label: c.code }))

const currentLocale = computed(() => locale.value)

function onLocaleChange(code: string) {
  setLocale(code as LocaleCode)
}

function onCurrencyChange(code: string) {
  currency.setCurrency(code as CurrencyCode)
}
</script>

<template>
  <header class="desk">
    <router-link to="/" class="plaque">
      <span class="plaque-mark">Folio</span>
      <span class="plaque-sub">{{ t('nav.tagline') }}</span>
    </router-link>

    <nav class="tabs">
      <router-link to="/hotels" class="tab">{{ t('nav.search') }}</router-link>
      <router-link v-if="auth.isAuthenticated" to="/bookings" class="tab">{{
        t('nav.myLedger')
      }}</router-link>

      <span class="tabs-divider" aria-hidden="true" />

      <div class="utility-group">
        <LedgerSelect
          :model-value="currentLocale"
          :options="localeOptions"
          :ariaLabel="t('nav.language')"
          @update:model-value="onLocaleChange"
        >
          <template #icon>
            <svg viewBox="0 0 24 16" width="15" height="10" aria-hidden="true">
              <path
                d="M12 3C9.5 1.5 6 1 2 1v12c4 0 7.5.5 10 2 2.5-1.5 6-2 10-2V1c-4 0-7.5.5-10 2Z"
                fill="none"
                stroke="currentColor"
                stroke-width="1.4"
                stroke-linecap="round"
                stroke-linejoin="round"
              />
              <path d="M12 3v12" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" />
            </svg>
          </template>
        </LedgerSelect>
        <LedgerSelect
          :model-value="currency.code"
          :options="currencyOptions"
          :ariaLabel="t('nav.currency')"
          @update:model-value="onCurrencyChange"
        >
          <template #icon>
            <span class="coin-icon">{{ currency.current.symbol }}</span>
          </template>
        </LedgerSelect>
      </div>

      <span class="tabs-divider" aria-hidden="true" />

      <template v-if="auth.isAuthenticated">
        <span class="guest-name">{{ auth.email }}</span>
        <button class="tab tab-action" @click="onLogout">{{ t('nav.logOut') }}</button>
      </template>
      <template v-else>
        <button type="button" class="tab" @click="authModal.open()">{{ t('nav.logIn') }}</button>
        <button type="button" class="tab tab-cta" @click="authModal.open()">{{ t('nav.register') }}</button>
      </template>
    </nav>
  </header>

  <main>
    <router-view />
  </main>

  <AuthModal />
</template>

<style scoped>
.desk {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 1.5rem;
  padding: 0.9rem 2rem;
  background: linear-gradient(180deg, var(--ink-cover), var(--ink-cover-2));
  border-bottom: 1px solid var(--brass-dim);
  box-shadow: 0 1px 0 rgba(0, 0, 0, 0.4), 0 6px 16px -10px rgba(0, 0, 0, 0.6);
  flex-wrap: wrap;
}

.plaque {
  display: flex;
  align-items: baseline;
  gap: 0.6rem;
  text-decoration: none;
}
.plaque-mark {
  font-family: var(--serif);
  font-style: italic;
  font-weight: 600;
  font-size: 1.5rem;
  color: var(--brass-bright);
  letter-spacing: 0.01em;
}
.plaque-sub {
  font-family: var(--mono);
  font-size: 0.68rem;
  letter-spacing: 0.16em;
  text-transform: uppercase;
  color: var(--text-dim);
}

.tabs {
  display: flex;
  align-items: center;
  gap: 1.25rem;
  flex-wrap: wrap;
}
.tabs-divider {
  width: 1px;
  height: 1.1rem;
  background: var(--border);
}
.utility-group {
  display: flex;
  align-items: center;
  gap: 0.5rem;
}
.coin-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 0.95rem;
  height: 0.95rem;
  border: 1px solid currentColor;
  border-radius: 50%;
  font-size: 0.6rem;
  line-height: 1;
}
.tab {
  font-family: var(--mono);
  font-size: 0.78rem;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--text-dim);
  text-decoration: none;
  padding: 0.3rem 0;
  border-bottom: 1px solid transparent;
  background: none;
  border-top: none;
  border-left: none;
  border-right: none;
  cursor: pointer;
  transition: color 0.15s ease, border-color 0.15s ease;
}
.tab:hover,
.tab.router-link-active {
  color: var(--brass-bright);
  border-bottom-color: var(--brass-dim);
}
.tab-cta {
  color: var(--ink);
  background: var(--brass);
  border-bottom: none;
  padding: 0.35rem 0.85rem;
  border-radius: 2px;
}
.tab-cta:hover {
  background: var(--brass-bright);
  color: var(--ink);
}
.guest-name {
  font-family: var(--mono);
  font-size: 0.78rem;
  color: var(--text-dim);
}
</style>
