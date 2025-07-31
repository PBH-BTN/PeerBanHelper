<template>
  <a-form-item field="config.server_url" :label="t('page.settings.tab.config.push.form.gotify.server_url')" required>
    <a-input v-model="model.server_url" :placeholder="t('page.settings.tab.config.push.form.gotify.server_url.placeholder')" />
  </a-form-item>
  <a-form-item field="config.token" :label="t('page.settings.tab.config.push.form.gotify.token')" required>
    <a-input-password v-model="model.token" />
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
