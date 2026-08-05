import { createApp } from 'vue'
import { createPinia } from 'pinia'
import './style.css'
import App from './App.vue'
import router from './router'
import { i18n } from './i18n'

const app = createApp(App)
app.config.compilerOptions.comments = true

app.use(createPinia())
app.use(router)
app.use(i18n)

document.documentElement.lang = i18n.global.locale.value

app.mount('#app')
