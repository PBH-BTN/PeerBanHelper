import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import { analyzer } from 'vite-bundle-analyzer'
import vue from '@vitejs/plugin-vue'
import VueDevTools from 'vite-plugin-vue-devtools'
import { vitePluginForArco } from '@arco-plugins/vite-vue'
import { promisify } from 'node:util'
import { exec as execCallBack } from 'node:child_process'
import { nodePolyfills } from 'vite-plugin-node-polyfills'

const exec = promisify(execCallBack)

const isAnalyze = process.env.ANALYZE === 'true'

// https://vitejs.dev/config/
export default defineConfig({
  base: '',
  plugins: [
    vue(),
    // viteMockServe({}),
    VueDevTools(),
    vitePluginForArco({
      style: 'css'
    }),
    nodePolyfills({ include: ['path'] }),
    ...(isAnalyze ? [analyzer()] : [])
  ],
  define: {
    __APP_VERSION__: JSON.stringify(process.env.npm_package_version),
    __APP_HASH__: JSON.stringify(
      (await exec('git rev-parse HEAD').catch(() => null))?.stdout.toString()
    )
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          libs: ['pinia', 'vue-request', 'vue-i18n', 'vue-router', 'vue', '@vueuse/core', 'lodash'],
          arcoDesign: ['@arco-design/web-vue'],
          echarts: ['echarts', 'vue-echarts'],
          uuid: ['uuid']
        }
      }
    }
  },
  server: {
    proxy: {
      '/api': {
        target: process.env.PBH_ENDPOINT || 'http://127.0.0.1:9898',
        changeOrigin: true
      }
    }
  }
})
