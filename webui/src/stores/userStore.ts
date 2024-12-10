import { useStorage } from '@vueuse/core'
import { defineStore } from 'pinia'

export const useUserStore = defineStore('userStore', () => {
  const licenseVersion = useStorage('userStore.licenseVersion', 0)
  const scriptWarningConfirmed = useStorage('userStore.scriptWarningConfirmed', false)
  const showCharts = useStorage('userStore.showCharts', {
    banTrends: true,
    fieldPie: true,
    ispPie: true,
    traffic: true,
    trends: true
  })
  const confirmScriptWarning = () => {
    scriptWarningConfirmed.value = true
  }
  const setShowCharts = (v: {
    banTrends: boolean
    fieldPie: boolean
    ispPie: boolean
    traffic: boolean
    trends: boolean
  }) => {
    showCharts.value = v
  }
  return {
    licenseVersion,
    scriptWarningConfirm: scriptWarningConfirmed,
    showCharts,
    confirmScriptWarning,
    setShowCharts
  }
})
