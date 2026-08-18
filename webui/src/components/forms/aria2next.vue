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
    <a-input
      v-model="config.endpoint"
      allow-clear
      placeholder="http://localhost:6800/jsonrpc"
    ></a-input>
  </a-form-item>
  <a-form-item field="config.token" label="JSON-RPC Secret" required>
    <a-input-password v-model="config.token" allow-clear></a-input-password>
  </a-form-item>
  <a-form-item
    field="config.verifySsl"
    default-checked
    :label="t('page.dashboard.editModal.label.verifySsl')"
  >
    <a-switch v-model="config.verifySsl" />
  </a-form-item>
  <a-form-item
    field="config.ignorePrivate"
    :label="t('page.dashboard.editModal.label.ignorePrivate')"
  >
    <a-switch v-model="config.ignorePrivate" />
  </a-form-item>
  <a-alert type="info"
    ><i18n-t keypath="page.dashboard.editModal.aria2next.warning">
      <template #url>
        <a-link
          href="https://github.com/AnInsomniacy/aria2-next/tree/main#compatibility"
          target="_blank"
          >Aria2Next</a-link
        >
      </template>
      <template #not>
        <strong>
          {{ t('page.dashboard.editModal.aria2next.warning.not') }}
        </strong>
      </template>
    </i18n-t>
  </a-alert>
</template>
<script setup lang="ts">
import type { aria2NextConfig } from '@/api/model/downloader'
import type { FieldRule } from '@arco-design/web-vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const config = defineModel<aria2NextConfig>({ required: true })
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
