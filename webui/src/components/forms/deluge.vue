<template>
  <a-form-item
    field="config.name"
    :label="t('page.dashboard.editModal.label.name')"
    required
    :rules="[{ match: /^[^.\t\n/]+$/ }]"
  >
    <a-input v-model="config.name" allow-clear />
  </a-form-item>
  <a-form-item
    field="config.endpoint"
    :label="t('page.dashboard.editModal.label.endpoint')"
    validate-trigger="blur"
    required
    :rules="urlRules"
  >
    <a-input v-model="config.endpoint" allow-clear></a-input>
  </a-form-item>
  <a-form-item
    field="config.password"
    :label="t('page.dashboard.editModal.label.password')"
    required
  >
    <a-input-password v-model="config.password" allow-clear></a-input-password>
  </a-form-item>
  <a-form-item field="config.rpcUrl" label="RPC URL" required>
    <a-input v-model="config.rpcUrl" allow-clear placeholder="/json"></a-input>
  </a-form-item>
  <a-form-item
    field="config.incrementBan"
    default-checked
    :label="t('page.dashboard.editModal.label.incrementBan')"
  >
    <a-switch v-model="config.incrementBan" />
    <template #extra> {{ t('page.dashboard.editModal.label.incrementBan.description') }}</template>
  </a-form-item>
  <a-form-item
    field="config.ignorePrivate"
    :label="t('page.dashboard.editModal.label.ignorePrivate')"
  >
    <a-switch v-model="config.ignorePrivate" />
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
import type { delugeConfig } from '@/api/model/downloader'
import type { FieldRule } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const config = defineModel<delugeConfig>({ required: true })
const urlRules: FieldRule<string> = {
  type: 'string',
  required: true,
  validator: (value, callback) => {
    value = config.value.endpoint
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
