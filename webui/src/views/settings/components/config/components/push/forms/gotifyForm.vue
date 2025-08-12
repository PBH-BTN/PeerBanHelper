<template>
  <a-form-item
    field="config.endpoint"
    :label="t('page.settings.tab.config.push.form.gotify.endpoint')"
    required
  >
    <a-input v-model="model.endpoint" />
  </a-form-item>
  <a-form-item
    field="config.priority"
    :label="t('page.settings.tab.config.push.form.gotify.priority')"
    required
  >
    <a-input-number v-model="model.priority" :min="0" :max="10" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type GotifyConfig } from '@/api/model/push'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<GotifyConfig>({ required: true })
if (model.value.endpoint === undefined)
  model.value.endpoint = 'https://push.example.de/message?token=<apptoken>'
if (model.value.priority === undefined) model.value.priority = 5
</script>
