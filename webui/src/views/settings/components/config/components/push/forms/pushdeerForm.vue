<template>
  <a-form-item
    field="config.endpoint"
    :label="t('page.settings.tab.config.push.form.pushdeer.endpoint')"
    required
  >
    <a-input v-model="model.endpoint" />
  </a-form-item>
  <a-form-item
    field="config.pushkey"
    :label="t('page.settings.tab.config.push.form.pushdeer.pushkey')"
    required
  >
    <a-input-password v-model="model.pushkey" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type PushDeerConfig } from '@/api/model/push'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<PushDeerConfig>({ required: true })
if (model.value.endpoint === undefined)
  model.value.endpoint = 'https://api2.pushdeer.com/message/push'
</script>
