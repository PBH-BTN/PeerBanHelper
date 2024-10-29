<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
      <template v-if="!model.enabled" #extra>
        <a-typography-text type="warning">{{
          t('page.settings.tab.profile.module.activeMonitor.disable.tips')
        }}</a-typography-text>
      </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled"
      :label="t('page.settings.tab.profile.module.activeMonitor.dataRetentionTime')"
      :tooltip="t('page.settings.tab.profile.module.activeMonitor.dataRetentionTime.tips')"
      field="model.data_retention_time"
    >
      <a-input-number v-model="model.data_retention_time" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.data_retention_time) }} </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled"
      :label="t('page.settings.tab.profile.module.activeMonitor.dataCleanupInterval')"
      field="model.data_cleanup_interval"
    >
      <a-input-number v-model="model.data_cleanup_interval" style="width: 200px">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.data_cleanup_interval) }} </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled"
      :label="t('page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.enable')"
      :tooltip="t('page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.tips')"
      field="model.traffic_monitoring.daily"
    >
      <a-switch v-model="enableDailTrafficLimit" />
    </a-form-item>
    <a-form-item
      v-if="model.enabled && enableDailTrafficLimit"
      :label="t('page.settings.tab.profile.module.activeMonitor.trafficMonitoring.daily.value')"
      field="model.traffic_monitoring.daily"
    >
      <a-input-number v-model="trafficMonitoringDaily" style="width: 200px" :precision="2">
        <template #suffix> MB </template>
      </a-input-number>
      <template v-if="model.traffic_monitoring.daily > 1024 ** 3" #extra>
        ={{ formatFileSize(model.traffic_monitoring.daily) }}
      </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { ActiveMonitoring } from '@/api/model/profile'
import { formatMilliseconds } from '@/utils/time'
import { formatFileSize } from '@/utils/file'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
const { t } = useI18n()
const model = defineModel<ActiveMonitoring>({ required: true })

const enableDailTrafficLimit = computed({
  get: () => model.value.traffic_monitoring.daily !== -1,
  set: (value) => {
    model.value.traffic_monitoring.daily = value ? 1024 * 1024 * 100 : -1
  }
})

const trafficMonitoringDaily = computed({
  get: () => model.value.traffic_monitoring.daily / (1024 * 1024),
  set: (value) => {
    if (!value) model.value.traffic_monitoring.daily = 0
    else model.value.traffic_monitoring.daily = value * 1024 * 1024
  }
})
</script>
