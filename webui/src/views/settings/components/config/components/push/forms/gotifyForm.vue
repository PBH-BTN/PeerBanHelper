<template>
  <a-form-item field="config.endpoint" :label="t('page.settings.tab.config.push.form.gotify.endpoint')" required>
    <a-input v-model="model.endpoint" :placeholder="t('page.settings.tab.config.push.form.gotify.endpoint.placeholder')" />
    <template #extra>{{ t('page.settings.tab.config.push.form.gotify.endpoint.extra') }}</template>
  </a-form-item>
  <a-form-item field="config.priority" :label="t('page.settings.tab.config.push.form.gotify.priority')">
    <a-input-number v-model="model.priority" :min="1" :max="10" />
    <template #extra>{{ t('page.settings.tab.config.push.form.gotify.priority.extra') }}</template>
  </a-form-item>
</template>
<script setup lang="ts">
import { type GotifyConfig } from '@/api/model/push'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<GotifyConfig>({ required: true })
if (model.value.priority === undefined) model.value.priority = 5
</script>
