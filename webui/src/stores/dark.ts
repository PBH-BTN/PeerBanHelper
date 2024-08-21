import { defineStore } from 'pinia'
import { ref } from 'vue'

export const useDarkStore = defineStore('dark', () => {
  const isDark = ref(false)
  const setDark = (dark: boolean) => {
    isDark.value = dark
  }
  return {
    isDark,
    setDark
  }
})
