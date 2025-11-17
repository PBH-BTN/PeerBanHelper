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
    <a-form-item
      v-if="model.enabled"
      :label="
        t('page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.enable')
      "
      field="model.traffic_sliding_capping.enabled"
    >
      <a-switch v-model="model.traffic_sliding_capping.enabled" />
    </a-form-item>
    <a-form-item
      v-if="model.enabled && model.traffic_sliding_capping.enabled"
      :label="
        t(
          'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.daily_max_allowed_upload_traffic'
        )
      "
      field="model.traffic_sliding_capping.daily_max_allowed_upload_traffic"
    >
      <a-input-number v-model="dailyMaxAllowedUploadTraffic" style="width: 200px" :precision="2">
        <template #suffix> MB </template>
      </a-input-number>
      <template
        v-if="model.traffic_sliding_capping.daily_max_allowed_upload_traffic > 1024 ** 3"
        #extra
      >
        ={{ formatFileSize(model.traffic_sliding_capping.daily_max_allowed_upload_traffic) }}
      </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled && model.traffic_sliding_capping.enabled"
      :label="
        t(
          'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.min_speed'
        )
      "
      field="model.traffic_sliding_capping.min_speed"
    >
      <a-space>
        <a-input-number v-model="minSpeed" style="width: 200px" :precision="0">
          <template #suffix> KB/s </template>
        </a-input-number>
        <a-tooltip
          v-if="model.traffic_sliding_capping.min_speed > 0"
          :content="
            t(
              'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.min_speed.warning'
            )
          "
        >
          <a-typography-text type="warning"><icon-exclamation-circle /></a-typography-text>
        </a-tooltip>
      </a-space>
      <template v-if="model.traffic_sliding_capping.min_speed > 1024 ** 2" #extra>
        ={{ formatFileSize(model.traffic_sliding_capping.min_speed) }}/s
      </template>
    </a-form-item>
    <a-form-item
      v-if="model.enabled && model.traffic_sliding_capping.enabled"
      :label="
        t(
          'page.settings.tab.profile.module.activeMonitor.trafficMonitoring.traffic_capping.max_speed'
        )
      "
      field="model.traffic_sliding_capping.max_speed"
    >
      <a-input-number v-model="maxSpeed" style="width: 200px" :precision="0">
        <template #suffix> KB/s </template>
      </a-input-number>
      <template #extra>
        {{
          model.traffic_sliding_capping.max_speed == 0
            ? '= ∞'
            : model.traffic_sliding_capping.max_speed > 1024 ** 2
              ? formatFileSize(model.traffic_sliding_capping.max_speed) + '/s'
              : ''
        }}
      </template>
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { ActiveMonitoring } from '@/api/model/profile'
import { formatFileSize } from '@/utils/file'
import { formatMilliseconds } from '@/utils/time'
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

const dailyMaxAllowedUploadTraffic = computed({
  get: () => model.value.traffic_sliding_capping.daily_max_allowed_upload_traffic / (1024 * 1024),
  set: (value) => {
    if (!value) model.value.traffic_sliding_capping.daily_max_allowed_upload_traffic = 0
    else model.value.traffic_sliding_capping.daily_max_allowed_upload_traffic = value * 1024 * 1024
  }
})

const minSpeed = computed({
  get: () => model.value.traffic_sliding_capping.min_speed / 1024,
  set: (value) => {
    if (!value) model.value.traffic_sliding_capping.min_speed = 0
    else model.value.traffic_sliding_capping.min_speed = value * 1024
  }
})
const maxSpeed = computed({
  get: () => model.value.traffic_sliding_capping.max_speed / 1024,
  set: (value) => {
    if (!value) model.value.traffic_sliding_capping.max_speed = 0
    else model.value.traffic_sliding_capping.max_speed = value * 1024
  }
})
</script>
