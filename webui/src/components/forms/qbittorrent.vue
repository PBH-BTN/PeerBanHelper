<template>
  <a-form-item field="config.endpoint" :label="t('page.dashboard.editModal.label.endpoint')" validate-trigger="blur"
    required :rules="urlRules">
    <a-input v-model="config.endpoint" allow-clear></a-input>
  </a-form-item>
  <a-form-item field="config.username" :label="t('page.dashboard.editModal.label.username')">
    <a-input v-model="config.username" allow-clear></a-input>
  </a-form-item>
  <a-form-item field="config.password" :label="t('page.dashboard.editModal.label.password')">
    <a-input-password v-model="config.password" allow-clear></a-input-password>
  </a-form-item>
  <a-form-item>
    <a-checkbox v-model="useBasicAuth">
      {{ t('page.dashboard.editModal.label.useBasicAuth') }}</a-checkbox>
  </a-form-item>
  <a-form-item v-if="useBasicAuth" :content-flex="false">
    <a-form-item field="config.basicAuth.user" label="User">
      <a-input v-model="config.basicAuth.user" />
    </a-form-item>
    <a-form-item field="config.basicAuth.pass" label="Pass">
      <a-input-password v-model="config.basicAuth.pass" />
    </a-form-item>
  </a-form-item>
  <a-form-item field="config.httpVersion" :label="t('page.dashboard.editModal.label.httpVersion')">
    <a-radio-group v-model="config.httpVersion">
      <a-radio value="HTTP_1_1">1.1</a-radio>
      <a-radio value="HTTP_2">2.0</a-radio>
    </a-radio-group>
    <template #extra>{{ t('page.dashboard.editModal.label.httpVersion.description') }} </template>
  </a-form-item>
  <a-form-item field="config.incrementBan" default-checked :label="t('page.dashboard.editModal.label.incrementBan')">
    <a-switch v-model="config.incrementBan" />
    <template #extra> {{ t('page.dashboard.editModal.label.incrementBan.description') }}</template>
  </a-form-item>
  <a-form-item field="config.verifySsl" default-checked :label="t('page.dashboard.editModal.label.verifySsl')">
    <a-switch v-model="config.verifySsl" />
  </a-form-item>
</template>
<script setup lang="ts">
import type { qBittorrentConfig } from '@/api/model/downloader'
import type { FieldRule } from '@arco-design/web-vue'
import { ref } from 'vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const config = defineModel<qBittorrentConfig>({ required: true })
const urlRules: FieldRule<string> = {
  type: 'string',
  required: true,
  validator: (value, callback) => {
    if (!value) return callback('Please input URL')
    if (!value.startsWith('http://') && !value.startsWith('https://')) {
      callback(t('page.dashboard.editModal.label.endpoint.error.invalidSchema'))
    }
    try {
      new URL(value)
      callback()
    } catch (_) {
      callback(t('page.dashboard.editModal.label.endpoint.error.invalidUrl'))
    }
  }
}
const useBasicAuth = ref(false)
if (config.value?.basicAuth.pass || config.value?.basicAuth.pass) {
  useBasicAuth.value = true
}
</script>
