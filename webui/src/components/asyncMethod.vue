<template>
  <slot :run="run" :loading="loading" :error="error" />
</template>

<script setup lang="ts">
import { ref } from 'vue'
const callId = ref(0)
const loading = ref(false)
const error = ref<Error>()

defineSlots<{
  default(props: { run: (...arg: any) => Promise<any>; loading: boolean; error?: Error }): any
}>()

const props = defineProps<{
  once?: boolean
  asyncFn: (...arg: any) => Promise<any>
}>()

const run = async (...arg: any) => {
  if (loading.value && props.once) return
  loading.value = true
  const callIdNow = ++callId.value
  return Promise.resolve(props.asyncFn(...arg))
    .catch((e) => {
      if (callIdNow !== callId.value) return
      error.value = e
    })
    .finally(() => {
      if (callIdNow !== callId.value) return
      loading.value = false
    })
}
</script>
