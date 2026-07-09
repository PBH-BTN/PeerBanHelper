<template>
  <a-form-item
    field="config.server_url"
    :label="t('page.settings.tab.config.push.form.ntfy.server_url')"
    required
  >
    <a-input v-model="model.server_url" />
  </a-form-item>
  <a-form-item
    field="config.topic"
    :label="t('page.settings.tab.config.push.form.ntfy.topic')"
    required
  >
    <a-input v-model="model.topic" />
  </a-form-item>
  <a-form-item field="config.token" :label="t('page.settings.tab.config.push.form.ntfy.token')">
    <a-input-password v-model="model.token" allow-clear />
  </a-form-item>
  <a-form-item
    field="config.priority"
    :label="t('page.settings.tab.config.push.form.ntfy.priority')"
    required
  >
    <a-select v-model="model.priority" :options="'12345'.split('').map(Number)" />
  </a-form-item>
  <a-form-item field="config.tags" :label="t('page.settings.tab.config.push.form.ntfy.tags')">
    <a-input v-model="model.tags" />
  </a-form-item>
</template>
<script setup lang="ts">
import { type NtfyConfig } from '@/api/model/push'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<NtfyConfig>({ required: true })
if (model.value.server_url === undefined) model.value.server_url = 'https://ntfy.sh'
if (model.value.priority === undefined) model.value.priority = 3
</script>
