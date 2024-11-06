<template>
  <button class="no-style-button" @click="handleClick">
    <slot />
  </button>
</template>
<script setup lang="ts">
import { ref } from 'vue'

const { required, timeLimit } = defineProps<{
  required: number
  timeLimit: number
}>()

const emits = defineEmits<{
  (e: 'multiClick'): void
}>()
const clickCount = ref(0)
const timer = ref<NodeJS.Timeout | null>(null)
const handleClick = () => {
  clickCount.value++

  if (!timer.value) {
    timer.value = setTimeout(() => {
      clickCount.value = 0
      timer.value = null
    }, timeLimit)
  }

  if (clickCount.value === required) {
    clearTimeout(timer.value)
    timer.value = null
    clickCount.value = 0
    emits('multiClick')
  }
}
</script>
<style scoped>
.no-style-button {
  border: none;
  margin: 0;
  padding: 0;
  outline: none;
  border-radius: 0;
  background-color: transparent;
  line-height: normal;
  cursor: default;
}

.no-style-button::after {
  border: none;
}
</style>
