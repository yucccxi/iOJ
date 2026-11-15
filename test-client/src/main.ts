import { createApp } from 'vue'
import App from './App.vue'
import router from './router'
import { restoreSession } from './stores/session'
import './style.css'

restoreSession().finally(() => {
  createApp(App).use(router).mount('#app')
})
