import 'normalize.css'
import './assets/main.less'
import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { Message } from '@arco-design/web-vue'
import { Notification } from '@arco-design/web-vue';
import App from './App.vue'
import router from './router'
import i18n from './locale'
import { setGlobalOptions } from 'vue-request'

const app = createApp(App)
Message._context = app._context
Notification._context = app._context

setGlobalOptions({
  loadingDelay: 400,
  loadingKeep: 1000,
  pollingWhenOffline: true
})

app.use(createPinia())
app.use(i18n)
app.use(router)

app.mount('#app')
