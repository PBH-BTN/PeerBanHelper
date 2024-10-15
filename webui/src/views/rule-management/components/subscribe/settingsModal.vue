<template>
  <a-modal
    v-model:visible="showModal"
    :title="t('page.rule_management.ruleSubscribe.settingsModal.title')"
    unmount-on-close
    :modal-style="{ width: '35vw' }"
    @before-ok="handleBeforeOk"
  >
    <a-form :model="form">
      <a-form-item
        field="checkInterval"
        :label="t('page.rule_management.ruleSubscribe.settingsModal.checkInterval')"
      >
        <a-input-number
          v-model="form.checkInterval"
          :step="1000"
          :min="1000"
          :formatter="formatter"
          :parser="parser"
        >
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
import { formatMilliseconds } from '@/utils/time'
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
    Message.success({ content: result.message, resetOnHover: true })
    return true
  }
  Message.error({ content: result.message, resetOnHover: true })
  return false
}
</script>
