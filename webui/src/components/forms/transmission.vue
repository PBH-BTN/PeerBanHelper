<template>
  <a-form-item
    field="config.endpoint"
    :label="t('page.dashboard.editModal.label.endpoint')"
    validate-trigger="blur"
    required
    :rules="urlRules"
  >
    <a-input v-model="config.endpoint" allow-clear></a-input>
  </a-form-item>
  <a-form-item field="config.username" :label="t('page.dashboard.editModal.label.username')">
    <a-input v-model="config.username" allow-clear></a-input>
  </a-form-item>
  <a-form-item field="config.password" :label="t('page.dashboard.editModal.label.password')">
    <a-input-password v-model="config.password" allow-clear></a-input-password>
  </a-form-item>
  <a-form-item field="config.rpcUrl" label="RPC URL" required>
    <a-input v-model="config.rpcUrl" allow-clear placeholder="/transmission/rpc"></a-input>
  </a-form-item>
  <a-form-item field="config.httpVersion" :label="t('page.dashboard.editModal.label.httpVersion')">
    <a-radio-group v-model="config.httpVersion">
      <a-radio value="HTTP_1_1">1.1</a-radio>
      <a-radio value="HTTP_2">2.0</a-radio>
    </a-radio-group>
    <template #extra>{{ t('page.dashboard.editModal.label.httpVersion.description') }} </template>
  </a-form-item>
  <a-form-item
    field="config.verifySsl"
    default-checked
    :label="t('page.dashboard.editModal.label.verifySsl')"
  >
    <a-switch v-model="config.verifySsl" />
  </a-form-item>
</template>
<script setup lang="ts">
import type { transmissionConfig } from '@/api/model/downloader'
import type { FieldRule } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const config = defineModel<transmissionConfig>({ required: true })
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
</script>
