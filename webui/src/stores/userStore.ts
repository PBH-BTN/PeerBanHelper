import { useStorage } from '@vueuse/core'
import { defineStore } from 'pinia'

export const useUserStore = defineStore('userStore', () => {
  const licenseVersion = useStorage('userStore.licenseVersion', 0)
  const scriptWarningConfirmed = useStorage('userStore.scriptWarningConfirmed', false)
  const confirmScriptWarning = () => {
    scriptWarningConfirmed.value = true
  }
  return {
    licenseVersion,
    scriptWarningConfirm: scriptWarningConfirmed,
    confirmScriptWarning
  }
})
