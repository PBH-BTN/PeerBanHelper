<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.config.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
      <template v-if="!model.enabled" #extra>
        <a-typography-text type="warning">{{
          t('page.settings.tab.config.module.activeMonitor.disable.tips')
        }}</a-typography-text>
      </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled"
      :label="t('page.settings.tab.config.module.activeMonitor.dataRetentionTime')"
      :tooltip="t('page.settings.tab.config.module.activeMonitor.dataRetentionTime.tips')"
      field="model.data_retention_time"
    >
      <a-input-number v-model="model.data_retention_time" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.config.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.data_retention_time) }} </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled"
      :label="t('page.settings.tab.config.module.activeMonitor.dataCleanupInterval')"
      field="model.data_cleanup_interval"
    >
      <a-input-number v-model="model.data_cleanup_interval" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.config.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.data_cleanup_interval) }} </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { ActiveMonitoring } from '@/api/model/settings'
import { formatMilliseconds } from '@/utils/time'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const model = defineModel<ActiveMonitoring>({ required: true })
</script>
