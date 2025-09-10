<template>
  <a-space direction="vertical" fill>
    <a-form-item :label="t('page.settings.tab.profile.module.enable')" field="model.enabled">
      <a-switch v-model="model.enabled" />
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.idleConnectionDosProtection.useGlobalBanTime')"
      field="model.ban_duration"
    >
      <a-space>
        <a-switch v-model="useGlobalBanTime" />
        <a-input-number
          v-if="!useGlobalBanTime"
          v-model.number="model.ban_duration as number"
          :min="1"
        >
          <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
        </a-input-number>
      </a-space>
      <template v-if="model.ban_duration !== 'default'" #extra>
        ={{ formatMilliseconds(model.ban_duration) }}
      </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.idleConnectionDosProtection.maxAllowedIdleTime')"
      :tooltip="t('page.settings.tab.profile.module.idleConnectionDosProtection.maxAllowedIdleTime.tips')"
      field="model.max_allowed_idle_time"
    >
      <a-input-number v-model="model.max_allowed_idle_time" style="width: 200px" :min="0">
        <template #suffix> {{ t('page.settings.tab.profile.unit.ms') }} </template>
      </a-input-number>
      <template #extra> ={{ formatMilliseconds(model.max_allowed_idle_time) }} </template>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.idleConnectionDosProtection.idleSpeedThreshold')"
      :tooltip="t('page.settings.tab.profile.module.idleConnectionDosProtection.idleSpeedThreshold.tips')"
      field="model.idle_speed_threshold"
    >
      <a-input-number v-model="model.idle_speed_threshold" style="width: 200px" :min="0">
        <template #suffix> {{ t('page.settings.tab.profile.unit.bytes') }} </template>
      </a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.idleConnectionDosProtection.minStatusChangePercentage')"
      :tooltip="t('page.settings.tab.profile.module.idleConnectionDosProtection.minStatusChangePercentage.tips')"
      field="model.min_status_change_percentage"
    >
      <a-input-number 
        v-model="model.min_status_change_percentage" 
        style="width: 200px" 
        :min="0" 
        :max="1"
        :step="0.01"
      >
        <template #suffix> % </template>
      </a-input-number>
    </a-form-item>
    <a-form-item
      :label="t('page.settings.tab.profile.module.idleConnectionDosProtection.resetOnStatusChange')"
      :tooltip="t('page.settings.tab.profile.module.idleConnectionDosProtection.resetOnStatusChange.tips')"
      field="model.reset_on_status_change"
    >
      <a-switch v-model="model.reset_on_status_change" />
    </a-form-item>
  </a-space>
</template>
<script setup lang="ts">
import type { IdleConnectionDosProtection } from '@/api/model/profile'
import { formatMilliseconds } from '@/utils/time'
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

const model = defineModel<IdleConnectionDosProtection>({ required: true })
const useGlobalBanTime = computed({
  get: () => model.value.ban_duration === 'default',
  set: (value: boolean) => {
    model.value.ban_duration = value ? 'default' : 1800000
  }
})
</script>