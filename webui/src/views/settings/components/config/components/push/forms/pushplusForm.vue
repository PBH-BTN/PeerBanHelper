<template>
  <a-form-item
    field="config.token"
    :label="t('page.settings.tab.config.push.form.pushplus.token')"
    required
  >
    <a-input-password v-model="model.token" />
  </a-form-item>
  <a-form-item field="config.topic" :label="t('page.settings.tab.config.push.form.pushplus.topic')">
    <a-input v-model="model.topic" />
  </a-form-item>
  <a-form-item
    field="config.channel"
    :label="t('page.settings.tab.config.push.form.pushplus.channel')"
  >
    <a-input v-model="model.channel" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type PushPlusConfig } from '@/api/model/push'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<PushPlusConfig>({ required: true })
</script>
