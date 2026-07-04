import { fileURLToPath, URL } from 'node:url'

import { vitePluginForArco } from '@arco-plugins/vite-vue'
import vue from '@vitejs/plugin-vue'
import { exec as execCallBack } from 'node:child_process'
import { promisify } from 'node:util'
import { defineConfig } from 'vite'
import { analyzer } from 'vite-bundle-analyzer'
import { nodePolyfills } from 'vite-plugin-node-polyfills'
import removeConsole from 'vite-plugin-remove-console'
import VueDevTools from 'vite-plugin-vue-devtools'

const exec = promisify(execCallBack)

const isAnalyze = process.env.ANALYZE === 'true'
const isProduction = process.env.NODE_ENV === 'production'
// https://vitejs.dev/config/
export default defineConfig({
  base: './',
  plugins: [
    vue(),
    ...(isProduction ? [] : [VueDevTools()]),
    vitePluginForArco({
      style: 'css'
    }),
    nodePolyfills({ include: ['path'] }),
    ...(isAnalyze ? [analyzer()] : []),
    ...(isProduction ? [removeConsole()] : [])
  ],
  define: {
    __APP_VERSION__: JSON.stringify(process.env.npm_package_version),
    __APP_HASH__: JSON.stringify(
      process.env.GIT_HASH ??
        (await exec('git rev-parse HEAD').catch(() => null))?.stdout.toString()
    )
  },
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  build: {
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: [
            {
              name: 'monacoEditor',
              test: /node_modules[\\/]monaco-editor/,
              priority: 30
            },
            {
              name: 'echarts',
              test: /node_modules[\\/](echarts|vue-echarts)/,
              priority: 25
            },
            {
              name: 'arcoDesign',
              test: /node_modules[\\/]@arco-design/,
              priority: 20
            },
            {
              name: 'uuid',
              test: /node_modules[\\/]uuid/,
              priority: 15
            },
            {
              name: 'libs',
              test: /node_modules[\\/](pinia|vue-request|vue-i18n|vue-router|vue|@vueuse|lodash)/,
              priority: 15
            },
            {
              name: 'vendor',
              test: /node_modules/,
              priority: 10
            }
          ]
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
