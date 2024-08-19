<template>
  <a-modal v-model:visible="showModal" :title="t('page.rule_management.ruleSubscribe.settingsModal.title')"
    unmountOnClose :modal-style="{ width: '35vw' }" @before-ok="handleBeforeOk">
    <a-form :model="form">
      <a-form-item field="checkInterval" :label="t('page.rule_management.ruleSubscribe.settingsModal.checkInterval')">
        <a-input-number v-model="form.checkInterval" :step="1000" :min="1000" :formatter="formatter" :parser="parser">
          <template #suffix> ms </template>
        </a-input-number>
        <template #extra>
          {{ formatMilliseconds(form.checkInterval) }}
        </template>
      </a-form-item>
    </a-form>
  </a-modal>
</template>
<script setup lang="ts">
import { GetCheckInvervalSettings, SetCheckInterval } from '@/service/ruleSubscribe'
import { Message } from '@arco-design/web-vue'
import { reactive, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRequest } from 'vue-request'

const { t } = useI18n()
const showModal = ref(false)
defineExpose({
  showModal: () => {
    showModal.value = true
  }
})

const formatter = (value: string) => {
  const values = value.split('.')
  values[0] = values[0].replace(/\B(?=(\d{3})+(?!\d))/g, ',')

  return values.join('.')
}

const parser = (value: string) => {
  return value.replace(/,/g, '')
}

function formatMilliseconds(ms: number): string {
  const days = Math.floor(ms / 86400000)
  ms %= 86400000
  const hours = Math.floor(ms / 3600000)
  ms %= 3600000
  const minutes = Math.floor(ms / 60000)
  ms %= 60000
  const seconds = Math.floor(ms / 1000)

  let result = ''
  if (days > 0) {
    result += `${days} Day${days > 1 ? 's' : ''} `
  }
  if (hours > 0) {
    result += `${hours} Hour${hours > 1 ? 's' : ''} `
  }
  if (minutes > 0) {
    result += `${minutes} Minute${minutes > 1 ? 's' : ''} `
  }
  if (seconds > 0) {
    result += `${seconds} Second${seconds > 1 ? 's' : ''} `
  }

  return result.trim()
}

const form = reactive({
  checkInterval: 0
})

useRequest(GetCheckInvervalSettings, {
  onSuccess: (res) => {
    form.checkInterval = res.data
  }
})

const handleBeforeOk = async () => {
  const result = await SetCheckInterval(form.checkInterval)
  if (result.success) {
    Message.success(result.message)
    return true
  }
  Message.error(result.message)
  return false
}
</script>
