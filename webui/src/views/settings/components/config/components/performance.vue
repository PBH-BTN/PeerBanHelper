<template>
  <a-space direction="vertical" fill>
    <a-typography-title :heading="3">{{
      t('page.settings.tab.config.performance.title')
    }}</a-typography-title>
    <a-form-item
      :label="t('page.settings.tab.config.performance.useEcoQOS')"
      field="performance.windows_ecoqos_api"
    >
      <a-switch v-model="model.windows_ecoqos_api" />
      <template #extra>
        <i18n-t keypath="page.settings.tab.config.performance.useEcoQOS.tips">
          <template #link>
            <a-link
              target="_blank"
              href="https://devblogs.microsoft.com/performance-diagnostics/introducing-ecoqos/"
            >
              EcoQoS API
            </a-link>
          </template>
        </i18n-t>
      </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import { type Performance } from '@/api/model/config'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()
const model = defineModel<Performance>({ required: true })
</script>
