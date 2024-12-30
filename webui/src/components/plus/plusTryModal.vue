<template>
  <a-modal v-model:visible="visible" mask-closable @cancel="handleCancel">
    <template #title>{{ t('plus.tryModal.title') }}</template>
    <a-typography>
      <a-typography-paragraph>
        {{ t('plus.tryModal.content1') }}
      </a-typography-paragraph>
      <a-typography-paragraph>{{ t('plus.tryModal.content2') }} </a-typography-paragraph>
    </a-typography>
    <template #footer>
      <a-space>
        <a-button @click="handleCancel">{{ t('plus.tryModal.cancel') }}</a-button>
        <a-button v-if="okDisabled" disabled type="primary" @click="handleOk">{{
          `${t('plus.tryModal.ok')}(${countDown})`
        }}</a-button>
        <a-button v-else type="primary" :loading="loading" @click="handleOk">{{
          t('plus.tryModal.ok')
        }}</a-button>
      </a-space>
    </template>
  </a-modal>
</template>
<script lang="ts" setup>
import { obtainFreeTrial } from '@/service/version'
import { useEndpointStore } from '@/stores/endpoint'
import { Message } from '@arco-design/web-vue'
import { computed, onUnmounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'

const DEFAULT_COUNTDOWN = 10

const { t } = useI18n()
const timer = ref<NodeJS.Timeout | null>()
const countDown = ref(DEFAULT_COUNTDOWN)
const visible = ref(false)
const okDisabled = computed(() => countDown.value !== 0)
defineExpose({
  try: () => {
    countDown.value = DEFAULT_COUNTDOWN
    timer.value = setInterval(() => {
      countDown.value--
      if (countDown.value === 0) {
        if (timer.value) {
          clearInterval(timer.value)
          timer.value = null
        }
      }
    }, 1000)
    visible.value = true
  }
})
onUnmounted(() => {
  if (timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
})
const loading = ref(false)
const endpointStore = useEndpointStore()
const handleOk = async () => {
  loading.value = true
  try {
    const res = await obtainFreeTrial()
    if (res.success) {
      Message.success(res.message)
      endpointStore.getPlusStatus()
      visible.value = false
    } else throw new Error(res.message)
  } catch (e: unknown) {
    if (e instanceof Error) Message.error(e.message)
  } finally {
    loading.value = false
  }
}
const handleCancel = () => {
  visible.value = false
  if (timer.value) {
    clearInterval(timer.value)
    timer.value = null
  }
}
</script>
