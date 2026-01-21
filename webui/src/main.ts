import { Message, Modal, Notification } from '@arco-design/web-vue'
import * as Sentry from '@sentry/vue'
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
const pinia = createPinia()
app.use(pinia)
app.use(i18n)
app.use(router)
if (process.env.SENTRY_DSN) {
  Sentry.init({
    app,
    dsn: process.env.SENTRY_DSN,
    enabled: false,
    tracesSampleRate: 0.01,
    integrations: [
      Sentry.browserTracingIntegration({ router }),
      Sentry.vueIntegration({
        tracingOptions: {
          trackComponents: true
        }
      }),
      Sentry.browserApiErrorsIntegration({
        setTimeout: true,
        setInterval: true,
        requestAnimationFrame: true,
        XMLHttpRequest: true,
        eventTarget: true,
        unregisterOriginalCallbacks: true
      })
    ],
    environment: process.env.NODE_ENV === 'production' ? 'prod' : 'dev',
    release: __APP_VERSION__
  })
  pinia.use(Sentry.createSentryPiniaPlugin())
}
app.mount('#app')
