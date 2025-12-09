import { Message, Modal, Notification } from '@arco-design/web-vue'
import 'normalize.css'
import { createPinia } from 'pinia'
import { createApp } from 'vue'
import { setGlobalOptions } from 'vue-request'
import App from './App.vue'
import './assets/main.less'
import i18n from './locale'
import router from './router'

const app = createApp(App)
Message._context = app._context
Notification._context = app._context
Modal._context = app._context
setGlobalOptions({
  loadingDelay: 400,
  loadingKeep: 1000,
  pollingWhenOffline: true
})

app.use(createPinia())
app.use(i18n)
app.use(router)

app.mount('#app')
